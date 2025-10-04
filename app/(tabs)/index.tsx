import React, { useEffect, useState } from "react";
import {
  Button,
  NativeModules,
  PermissionsAndroid,
  Platform,
  SafeAreaView,
  TextInput,
  View,
} from "react-native";
import { BLEPrinter } from "react-native-thermal-receipt-printer";
const { BluetoothPrinterModule } = NativeModules;
export default function App() {
  useEffect(() => {
    if (Platform.OS == "android") {
      BLEPrinter.init().then(async () => {
        //list printers
        const print = await BLEPrinter.getDeviceList();
        console.log(print);
      });
    }
  }, []);

  const coneccted = async () => {
    const res = await BLEPrinter.connectPrinter("66:32:64:9A:65:3F");
    console.log(res);
  };
  const imprimir = async () => {
    const ticket = '[C]<qrcode size="20">https://example.com</qrcode>';

    BLEPrinter.printText(ticket);
    BLEPrinter.printBill(ticket);
  };
  const [state, setState] = useState({
    text:
      '[C]<u><font size="big">TEST IMPRESIÓN</font></u>\n' +
      "[L]\n" +
      "[C]--------------------------\n" +
      "[L]<b>Producto A</b>[R]$10.00\n" +
      "[L]<b>Producto B</b>[R]$20.00\n" +
      "[C]--------------------------\n" +
      "[R]TOTAL:[R]$30.00\n" +
      '[C]<qrcode size="20">https://example.com</qrcode>\n',
  });

  const requestBluetoothPermission = async () => {
    if (Platform.OS === "android") {
      await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT
      );
      await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION
      );
    }
  };

  const onPrintBluetooth = async () => {
    try {
      BluetoothPrinterModule.printText("Hola mundo desde Kotlin")
        .then((res: string) => {
          console.log(res);
        })
        .catch((err: any) => {
          console.error(err);
        });

      console.log("✅ Impresión bluetooth terminada");
    } catch (err) {
      console.log("❌ Error al imprimir por Bluetooth:", err);
    }
  };

  const onComenzar = async () => {
    try {
      BluetoothPrinterModule.startBackgroundService()
        .then((res: string) => {
          console.log(res);
        })
        .catch((err: any) => {
          console.error(err);
        });

      console.log("✅ Impresión bluetooth terminada");
    } catch (err) {
      console.log("❌ Error al imprimir por Bluetooth:", err);
    }
  };

  return (
    <SafeAreaView style={{ padding: 20 }}>
      <TextInput
        style={{ borderWidth: 1, padding: 10, marginBottom: 10 }}
        value={state.text}
        multiline
        onChangeText={(text) => setState((prev) => ({ ...prev, text }))}
      />
      <Button title="Imprimir por Bluetooth" onPress={onPrintBluetooth} />
      <View style={{ marginBottom: 20 }} />
      <Button title="Connectar" onPress={coneccted} />
      <View style={{ marginBottom: 20 }} />
      <Button title="impr" onPress={imprimir} />
      <View style={{ marginBottom: 20 }} />
      <Button title="permisos" onPress={requestBluetoothPermission} />

      <View style={{ marginBottom: 20 }} />
      <Button title="comenzar" onPress={onComenzar} />
    </SafeAreaView>
  );
}
