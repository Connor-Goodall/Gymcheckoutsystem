#include <stdio.h>

#include <zephyr/logging/log_output.h>
#include <zephyr/logging/log_ctrl.h>

#include <zephyr/kernel.h>
#include <zephyr/sys/reboot.h>
#include <zephyr/settings/settings.h>
#include <nfc/ndef/msg.h>
#include <nfc/ndef/text_rec.h>
#include <nfc_t2t_lib.h>

#include <stdbool.h>
#include <zephyr/types.h>
#include <zephyr/drivers/sensor.h>
#include <stddef.h>
#include <string.h>
#include <errno.h>
#include <zephyr/sys/printk.h>
#include <zephyr/sys/byteorder.h>
#include <zephyr/kernel.h>
#include <zephyr/timing/timing.h>
#include <zephyr/sys/arch_interface.h>
#include <zephyr/device.h>
#include <time.h>
#include <zephyr/drivers/gpio.h>
#include <zephyr/bluetooth/bluetooth.h>
#include <zephyr/bluetooth/hci.h>
#include <zephyr/bluetooth/conn.h>
#include <zephyr/bluetooth/uuid.h>
#include <zephyr/bluetooth/gatt.h>
#include <zephyr/sys/reboot.h>

#include <dk_buttons_and_leds.h>
#define BUTTON0_NODE DT_ALIAS(sw0)


#define BUTTON0_PIN DT_GPIO_PIN(BUTTON0_NODE, gpios)
#define BUTTON0_FLAGS DT_GPIO_FLAGS(BUTTON0_NODE, gpios)

#define MAX_REC_COUNT		3
#define NDEF_MSG_BUF_SIZE	128
#define NFC_FIELD_LED		DK_LED1

char message[32] = "treadmill7";
char ble_uuid[64] = "BDFC9792-8234-405E-AE02-35EF3274B299";
char en_code[2] = "en";
static uint8_t ndef_msg_buf[NDEF_MSG_BUF_SIZE];

static void ccc_cfg_changed(const struct bt_gatt_attr *attr, uint16_t value)
{
	// printk("Value: %d. Not sure what to do here\n", value);
}

static void ccc_cfg_timer(const struct bt_gatt_attr *attr, uint16_t value)
{
	// printk("Value: %d. Not sure what to do here\n", value);
}

#define LAB2_SERVICE_UUID BT_UUID_128_ENCODE(0xBDFC9792, 0x8234, 0x405E, 0xAE02, 0x35EF3274B299)

char queue[4][30] = {"", "", "", ""};
int queueNumber[4] = {0, 1, 2, 3};
struct bt_conn* connectionQueue[4] = {NULL, NULL, NULL, NULL};
int count = 0;
uint32_t start;
uint32_t end;
float conversion = 1000 * 60;
float timerConversion = 1000;
// Global value that saves state for the characteristic.
uint32_t characteristic_value = 0x7;
// Set up the advertisement data.
#define DEVICE_NAME "Treadmill7"
#define DEVICE_NAME_LEN (sizeof(DEVICE_NAME) - 1)

static K_SEM_DEFINE(ble_init_ok, 0, 1);

static struct k_work adv_work;
static ssize_t read_queue(struct bt_conn *conn,
			       const struct bt_gatt_attr *attr, void *buf,
			       uint16_t len, uint16_t offset);
static ssize_t read_timer(struct bt_conn *conn,
			       const struct bt_gatt_attr *attr, void *buf,
			       uint16_t len, uint16_t offset);
// Setup the the service and characteristics.
static const struct bt_data ad[] = {
	BT_DATA(BT_DATA_NAME_COMPLETE, DEVICE_NAME, DEVICE_NAME_LEN),
	BT_DATA_BYTES(BT_DATA_UUID128_ALL, LAB2_SERVICE_UUID)
};

// Setup the the service and characteristics.
BT_GATT_SERVICE_DEFINE(lab2_service,
	BT_GATT_PRIMARY_SERVICE(
		BT_UUID_DECLARE_128(LAB2_SERVICE_UUID)
	),

	BT_GATT_CHARACTERISTIC(BT_UUID_DECLARE_16(0x0001), BT_GATT_CHRC_READ, BT_GATT_PERM_READ, read_queue, NULL, NULL),
	BT_GATT_CHARACTERISTIC(BT_UUID_DECLARE_16(0x0002), BT_GATT_CHRC_READ, BT_GATT_PERM_READ, read_timer, NULL, NULL),
	BT_GATT_CHARACTERISTIC(BT_UUID_DECLARE_16(0x0003), BT_GATT_CHRC_NOTIFY, BT_GATT_PERM_NONE, NULL, NULL, NULL),
	BT_GATT_CCC(ccc_cfg_timer, BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),
	BT_GATT_CHARACTERISTIC(BT_UUID_DECLARE_16(0x0004), BT_GATT_CHRC_NOTIFY, BT_GATT_PERM_NONE, NULL, NULL, NULL),
	BT_GATT_CCC(ccc_cfg_changed, BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),


);

static ssize_t read_queue(struct bt_conn *conn,
			       const struct bt_gatt_attr *attr, void *buf,
			       uint16_t len, uint16_t offset)
{
	char addr[BT_ADDR_LE_STR_LEN];
	bt_addr_le_to_str(bt_conn_get_dst(conn), addr, sizeof(addr));
	for (int i = 0; i < 4; i++){
		if(strcmp(addr, queue[i]) == 0){
			printk("%s\n", addr);
			printk("%u\n", queueNumber[i]);
			printk("%u\n", i);
			return bt_gatt_attr_read(conn, attr, buf, len, offset, &queueNumber[i],
				 sizeof(queueNumber[i]));
		}
	}
}

static ssize_t read_timer(struct bt_conn *conn,
			       const struct bt_gatt_attr *attr, void *buf,
			       uint16_t len, uint16_t offset)
{
	char addr[BT_ADDR_LE_STR_LEN];
	uint32_t timer = 60 - (uint32_t) ((end - start) / timerConversion);
	bt_addr_le_to_str(bt_conn_get_dst(conn), addr, sizeof(addr));
	for (int i = 0; i < 4; i++){
		if(strcmp(addr, queue[i]) == 0){
			printk("Timer");
			return bt_gatt_attr_read(conn, attr, buf, len, offset, &timer,
				 sizeof(timer));
		}
	}
}
// callback function for when an NFC field is detected
static void nfc_callback(void *context, nfc_t2t_event_t event, const uint8_t *data, size_t data_length) {
	ARG_UNUSED(context);
	ARG_UNUSED(data);
	ARG_UNUSED(data_length);
	
	// activate LED1 when an NFC field is detected
	switch (event) {
		case NFC_T2T_EVENT_FIELD_ON:
			uint32_t seconds = k_uptime_get() / 1000;
			uint32_t hours = seconds / 3600U;
			seconds -= hours * 3600U;
			uint32_t minutes = seconds / 60U;
			seconds -= minutes * 60U;
			printk("[%02u:%02u:%02u] nfc field detected\n", hours, minutes, seconds) ;
			dk_set_led_on(NFC_FIELD_LED);
			break;
		case NFC_T2T_EVENT_FIELD_OFF:
			dk_set_led_off(NFC_FIELD_LED);
			break;
		default:
			break;
	}
}

// encode message and ble_uuid
static int encode_data(uint8_t* buffer, uint32_t* len) {
	// create NFC NDEF text record description for nfc_message and nfc_ble_uuid
	NFC_NDEF_TEXT_RECORD_DESC_DEF(nfc_message, UTF_8, en_code, sizeof(en_code), message, sizeof(message));
	NFC_NDEF_TEXT_RECORD_DESC_DEF(nfc_ble_uuid, UTF_8, en_code, sizeof(en_code), ble_uuid, sizeof(ble_uuid));

	// create NFC NDEF message description
	NFC_NDEF_MSG_DEF(nfc_msg, MAX_REC_COUNT);

	// add records to NDEF message
	nfc_ndef_msg_record_add(&NFC_NDEF_MSG(nfc_msg), &NFC_NDEF_TEXT_RECORD_DESC(nfc_message));
	nfc_ndef_msg_record_add(&NFC_NDEF_MSG(nfc_msg), &NFC_NDEF_TEXT_RECORD_DESC(nfc_ble_uuid));

	// encode the message
	nfc_ndef_msg_encode(&NFC_NDEF_MSG(nfc_msg), buffer, len);
	
	return 0;
}
static void adv_continue(void) {
	struct bt_le_adv_param adv_param;

    int err;

    adv_param = *BT_LE_ADV_CONN;
	adv_param.options |= BT_LE_ADV_OPT_NONE;
    err = bt_le_adv_start(&adv_param, ad, ARRAY_SIZE(ad),
                NULL, 0);
    if (err) {
        printk("Advertising failed to start (err %d)\n", err);
        return;
    }

    printk("Regular advertising started\n");
}
static void adv_handler(struct k_work *work) {
    adv_continue();
}


static void connected(struct bt_conn *conn, uint8_t err)
{
	char addr[BT_ADDR_LE_STR_LEN];
	if (err) {
		printk("Connection failed (err 0x%02x)\n", err);
	} else {
		bt_addr_le_to_str(bt_conn_get_dst(conn), addr, sizeof(addr));
		printk("Connected\n");
		printk("Address: %s\n", addr);
		strcpy(queue[count], addr);
		connectionQueue[count] = conn;
		if(count == 0){
			start = k_uptime_get_32();
		}
		count++;
	}
}


static void disconnected(struct bt_conn *conn, uint8_t reason)
{
	printk("Disconnected (reason 0x%02x)\n", reason);
	int aboveNumber = 0;
	char addr[BT_ADDR_LE_STR_LEN];
	bt_addr_le_to_str(bt_conn_get_dst(conn), addr, sizeof(addr));
	for(int i = 0; i < 4; i++){
		if(strcmp(addr, queue[i]) == 0 || aboveNumber == 1){
			if(i < 3){
				strcpy(queue[i], queue[i + 1]);
				connectionQueue[i] = connectionQueue[i + 1];
				if(connectionQueue[i] != NULL){
					printk("NOTIFY");
					bt_gatt_notify(connectionQueue[i], &lab2_service.attrs[9], &queueNumber[i], sizeof(queueNumber[i]));
				}
			}
			else{
				strcpy(queue[i], "");
				connectionQueue[i] = NULL;
				count--;
			}
			aboveNumber = 1;
		}
	}
	if(connectionQueue[0] != NULL){
		start =  k_uptime_get_32();
	}
}
static struct bt_conn_cb bt_conn_callbacks = {
    .connected = connected,
    .disconnected = disconnected,
};



static void adv_start(void) {
    k_work_submit(&adv_work);
}

static void bt_ready(int err)
{
	if (err) {
		printk("Bluetooth init failed (err %d)\n", err);
		return;
	}

	printk("Bluetooth initialized\n");
	bt_conn_cb_register(&bt_conn_callbacks);
	adv_start();
	k_sem_give(&ble_init_ok);
}



int main() {
	uint32_t len = sizeof(ndef_msg_buf);
	int err;

	printk("Initializing nfc tag...\n");

	// configure LED-pins as outputs
	err = dk_leds_init();
	if (err == 0)
		printk("LEDs initialized successfully\n");
	else {
		printk("failed to initialize LEDs! (err %d)\n", err);
		goto fail;
	}
	// set up NFC
	err = nfc_t2t_setup(nfc_callback, NULL);
	if (err == 0)
		printk("nfc T2T library configured successfully\n");
	else {
		printk("failed to configure NFC T2T library! (err %d)\n", err);
		goto fail;
	}
	// encode data
	err = encode_data(ndef_msg_buf, &len);
	if (err == 0)
		printk("data encoded successfully\n");
	else {
		printk("failed to encode data! (err %d)\n", err);
		goto fail;
	}
	// set NFC payload
	err = nfc_t2t_payload_set(ndef_msg_buf, len);
	if (err == 0)
		printk("nfc payload configured successfully\n");
	else {
		printk("failed to configure nfc payload! (err %d)\n", err);
		goto fail;
	}
	// start NFC emulation
	err = nfc_t2t_emulation_start();
	if (err == 0)
		printk("emulation started successfully\n");
	else {
		printk("failed to start emulation! (err %d)\n", err);
		goto fail;
	}
	printk("nfc tag initialized successfully\n");
	printk("\nNDEF records:\n  name: nfc_message\n  text: %s\n\n  name: nfc_ble_uuid\n  text: %s\n\nwaiting for nfc field detection...\n", message, ble_uuid);
	k_work_init(&adv_work, adv_handler);
	err = bt_enable(bt_ready);
	if (err) {
		printk("Bluetooth init failed (err %d)\n", err);
		return 0;
	}
	err = k_sem_take(&ble_init_ok, K_MSEC(500));
	if(!err){
		printk("BLE initialized\n");
	}
	else {
        printk("BLE initialization did not complete in time\n");
    }
	uint8_t button0_state = 0;
	uint8_t button0_current_state = 0;


	// get the GPIO device
	static const struct gpio_dt_spec button = GPIO_DT_SPEC_GET_OR(BUTTON0_NODE, gpios, {0});

	// configure the button pin as input
	gpio_pin_configure_dt(&button, GPIO_INPUT);
	while (1) {
		if(connectionQueue[0] != NULL){
			end =  k_uptime_get_32();
			if(((end - start) / conversion) >= 1){
				struct bt_conn* connection = connectionQueue[0];
				err = bt_conn_disconnect(connection, BT_HCI_ERR_REMOTE_USER_TERM_CONN);
			}
		}
		// if the button state changes, store the time
		button0_current_state = gpio_pin_get_dt(&button);
		if (button0_state != button0_current_state && connectionQueue[0] != NULL) {
			button0_state = button0_current_state;
			if (button0_current_state == 1) {
				struct bt_conn* connection = connectionQueue[0];
				err = bt_conn_disconnect(connection, BT_HCI_ERR_REMOTE_USER_TERM_CONN);
			}
		}
		for (int i = 0; i < 4; i++){
			if(connectionQueue[i] != NULL){
				uint32_t timer = 60 - (uint32_t) ((end - start) / timerConversion);
				printk("%u\n", timer);
				bt_gatt_notify(connectionQueue[i], &lab2_service.attrs[5], &timer, sizeof(timer));
			}
			if(strcmp(queue[i], "") != 0){
				printk("%u", queueNumber[i]);
				printk(" ");
				printk("%s\n", queue[i]);
				k_sleep(K_MSEC(1000));
			}

		}
	}
	return 0;

fail:
printk("rebooting...");
#if CONFIG_REBOOT
	sys_reboot(SYS_REBOOT_COLD);
#endif
	return -EIO;
} 