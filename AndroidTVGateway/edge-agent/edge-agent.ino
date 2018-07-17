#include <SoftwareSerial.h>
#include <Adafruit_Sensor.h>
#include <DHT.h>
#include <DHT_U.h>

#define DHTTYPE           DHT11
#define WINDOWPIN         4
#define ACPIN             5
#define DHTPIN            6
#define DOOR_LOCK         7
#define LED_LIGHT         8

DHT dht(DHTPIN, DHTTYPE);
SoftwareSerial XBee(2, 3); // RX, TX
uint8_t successRead;    // Variable integer to keep if we have Successful Read from Reader
byte readCard[4];   // Stores scanned ID read from RFID Module
int lastId = 0;
char msg[100];
boolean isBulbOn = false;
boolean isOpen = false;

void setup() {
  XBee.begin(9600);
  Serial.begin(9600);
  dht.begin();
  pinMode(WINDOWPIN, INPUT);
  pinMode(ACPIN, INPUT);
  pinMode(LED_LIGHT, OUTPUT);
  pinMode(DOOR_LOCK, OUTPUT);
  digitalWrite(DOOR_LOCK, HIGH);
}

void loop() {
  if (XBee.available()) {
    String incomingMsg = XBee.readString();
    Serial.println(incomingMsg);
    if (incomingMsg == "LON\r") {
      digitalWrite(LED_LIGHT, HIGH);
      XBee.print("{\"a\":\"LON\"}\r");
      isBulbOn = true;
    } else if (incomingMsg == "LOFF\r") {
      digitalWrite(LED_LIGHT, LOW);
      XBee.print("{\"a\":\"LOFF\"}\r");
      isBulbOn = false;
    }else if (incomingMsg == "DOPEN\r") {
      digitalWrite(DOOR_LOCK, LOW);
      XBee.print("{\"a\":\"DOPEN\"}\r");
      isOpen = true;
    } else if (incomingMsg == "DCLOSE\r") {
      digitalWrite(DOOR_LOCK, HIGH);
      XBee.print("{\"a\":\"DCLOSE\"}\r");
      isOpen = false;
    } else if (incomingMsg == "D\r") {
      syncNode();
    }
  }
}

void syncNode() {
  float h = dht.readHumidity();
  float t = dht.readTemperature();
  int window = digitalRead(WINDOWPIN);
  int ac = digitalRead(ACPIN);
  char syncmsg[100];
  sprintf (syncmsg, "{\"a\":\"DATA\",\"p\":{\"t\":%d,\"h\":%d,\"a\":%d,\"w\":%d,\"d\":%d,\"l\":%d}}\r", (int) t, (int) h, ac, window, isOpen, isBulbOn);
  XBee.print(syncmsg);
  Serial.println(syncmsg);
}

