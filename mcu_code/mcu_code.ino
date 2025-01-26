#include "Arduino.h"
#include "HT_st7735.h"
#include "LoRaWan_APP.h"
#include <Bounce2.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include <BLE2901.h>

#define SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define WRITE_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define NOTIFY_UUID "2c00026e-ce18-43b1-b24e-392346089fcc"

BLEServer *pServer = NULL;
BLECharacteristic *pCharacteristicNt = NULL;
BLE2901 *descriptor_2901 = NULL;

#define ID 2

#define BUTTON_PIN 0
Bounce button = Bounce();

// --- DISPLAY ---

HT_st7735 st7735;

#define FONT Font_7x10
#define LINE_HEIGHT 10
#define MAX_LINES 8
#define MAX_CHARS 32

#define ST7735_GRAY 0x7BEF

char consoleLines[MAX_LINES][MAX_CHARS];
uint16_t consoleColors[MAX_LINES];
int lineCount = 0;

unsigned long prevMillis = 0;
const unsigned long interval = 1000;

// --- LORA ---

#define RF_FREQUENCY 865000000  // Hz

#define TX_OUTPUT_POWER 21  // dBm

#define LORA_BANDWIDTH 0         // [0: 125 kHz, \
                                 //  1: 250 kHz, \
                                 //  2: 500 kHz, \
                                 //  3: Reserved]
#define LORA_SPREADING_FACTOR 12  // [SF7..SF12]
#define LORA_CODINGRATE 4        // [1: 4/5, \
                                 //  2: 4/6, \
                                 //  3: 4/7, \
                                 //  4: 4/8]
#define LORA_PREAMBLE_LENGTH 8   // Same for Tx and Rx
#define LORA_SYMBOL_TIMEOUT 0    // Symbols
#define LORA_FIX_LENGTH_PAYLOAD_ON false
#define LORA_IQ_INVERSION_ON false


#define RX_TIMEOUT_VALUE 1000
#define BUFFER_SIZE 30  // Define the payload size here

char txpacket[BUFFER_SIZE];
char rxpacket[BUFFER_SIZE];

static RadioEvents_t RadioEvents;
void OnTxDone(void);
void OnTxTimeout(void);
void OnRxDone(uint8_t *payload, uint16_t size, int16_t rssi, int8_t snr);

int16_t txNumber;
bool sleepMode = false;
int16_t Rssi, rxSize;

// --- main code ---

int msg_count = 0;

void drawLine(int index) {
  st7735.st7735_write_str(
    0,
    index * LINE_HEIGHT,
    consoleLines[index],
    FONT,
    consoleColors[index],
    ST7735_BLACK);
}

void redrawAll() {
  st7735.st7735_fill_screen(ST7735_BLACK);

  for (int i = 0; i < lineCount; i++) {
    drawLine(i);
  }
}

void scrollUp() {
  for (int i = 0; i < MAX_LINES - 1; i++) {
    strcpy(consoleLines[i], consoleLines[i + 1]);
    consoleColors[i] = consoleColors[i + 1];
  }
}

void addLine(const char *text, uint16_t color) {
  if (lineCount < MAX_LINES) {
    strncpy(consoleLines[lineCount], text, MAX_CHARS);
    consoleLines[lineCount][MAX_CHARS - 1] = '\0';
    consoleColors[lineCount] = color;

    drawLine(lineCount);
    lineCount++;
  } else {
    scrollUp();

    strncpy(consoleLines[MAX_LINES - 1], text, MAX_CHARS);
    consoleLines[MAX_LINES - 1][MAX_CHARS - 1] = '\0';
    consoleColors[MAX_LINES - 1] = color;

    redrawAll();
  }
}

void OnTxDone(void) {
  Radio.Rx(0);
}

void OnTxTimeout(void) {
  Radio.Sleep();
  Serial.print(">>> TX Timeout ???");
}

bool deviceConnected = false;
uint32_t value = 0;

void OnRxDone(uint8_t *payload, uint16_t size, int16_t rssi, int8_t snr) {
  Rssi = rssi;
  rxSize = size;
  memcpy(rxpacket, payload, size);
  rxpacket[size] = '\0';
  Serial.printf(">>> RX: %s - rssi %d length %d", rxpacket, Rssi, rxSize);
  char buff[100] = {};
  sprintf(buff, "< \"%s\"", rxpacket);
  if (strstr(buff, "ALARM") != NULL) {
    addLine(buff, ST7735_RED);
  } else {
    addLine(buff, ST7735_GREEN);
  }
  if (deviceConnected) {
    pCharacteristicNt->setValue((uint8_t *)buff, strlen(buff));
    pCharacteristicNt->notify();
    value++;
    delay(500);
  }
}

class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *pServer) {
    deviceConnected = true;
  };

  void onDisconnect(BLEServer *pServer) {
    deviceConnected = false;
  }
};

class MyCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) {
    String value = pCharacteristic->getValue();

    if (value.length() > 0) {
      sprintf(txpacket, "#%d %s", ID, value);
      Serial.printf(">>> TX: %s length %d", txpacket, strlen(txpacket));
      Radio.Send((uint8_t *)txpacket, strlen(txpacket));
      char buff[100] = {};
      sprintf(buff, "> \"%s\"", txpacket);
      addLine(buff, ST7735_GRAY);
    }
  }
};

void setup() {
  Serial.begin(115200);

  pinMode(BUTTON_PIN, INPUT_PULLUP);
  button.attach(BUTTON_PIN, INPUT_PULLUP);
  button.interval(50);

  st7735.st7735_init();
  st7735.st7735_fill_screen(ST7735_BLACK);

  Mcu.begin(HELTEC_BOARD, SLOW_CLK_TPYE);
  txNumber = 0;
  Rssi = 0;

  RadioEvents.TxDone = OnTxDone;
  RadioEvents.TxTimeout = OnTxTimeout;
  RadioEvents.RxDone = OnRxDone;

  Radio.Init(&RadioEvents);
  Radio.SetChannel(RF_FREQUENCY);
  Radio.SetTxConfig(MODEM_LORA, TX_OUTPUT_POWER, 0, LORA_BANDWIDTH,
                    LORA_SPREADING_FACTOR, LORA_CODINGRATE,
                    LORA_PREAMBLE_LENGTH, LORA_FIX_LENGTH_PAYLOAD_ON,
                    true, 0, 0, LORA_IQ_INVERSION_ON, 3000);

  Radio.SetRxConfig(MODEM_LORA, LORA_BANDWIDTH, LORA_SPREADING_FACTOR,
                    LORA_CODINGRATE, 0, LORA_PREAMBLE_LENGTH,
                    LORA_SYMBOL_TIMEOUT, LORA_FIX_LENGTH_PAYLOAD_ON,
                    0, true, 0, 0, LORA_IQ_INVERSION_ON, true);
  Radio.Rx(0);

  BLEDevice::init("MeshGuard_" + String(ID));
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  BLEService *pService = pServer->createService(SERVICE_UUID);

  BLECharacteristic *pCharacteristicWr =
    pService->createCharacteristic(WRITE_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE);
  pCharacteristicWr->setCallbacks(new MyCallbacks());
  pCharacteristicWr->setValue("");

  pCharacteristicNt = pService->createCharacteristic(NOTIFY_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  pCharacteristicNt->addDescriptor(new BLE2902());

  pService->start();
  BLEAdvertising *pAdvertising = pServer->getAdvertising();
  pAdvertising->start();

  char buff[100] = {};
  sprintf(buff, "--- Device ID:%d ---", ID);
  addLine(buff, ST7735_GREEN);
}

void loop() {

  Radio.IrqProcess();

  button.update();
  if (button.fell()) {
    sprintf(txpacket, "#%d %s", ID, "ALARM");
    Serial.printf(">>> TX: %s length %d", txpacket, strlen(txpacket));
    Radio.Send((uint8_t *)txpacket, strlen(txpacket));
    char buff[100] = {};
    sprintf(buff, "> \"%s\"", txpacket);
    addLine(buff, ST7735_GRAY);
  }

  // unsigned long currentMillis = millis();
  // if (currentMillis - prevMillis >= interval) {
  //   prevMillis = currentMillis;
  //   static int counter = 0;
  //   char buf[MAX_CHARS];
  //   sprintf(buf, "Line number #%d", counter++);
  //   addLine(buf);
  // }
}
