import { createSocketCutom } from "@/hooks/socket";
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
  const socket = createSocketCutom();
interface PrintSaleDTO {
  transmitterId: number;
  details: {
    name: string;
    quantity: number;
    price: number;
    discount: number;
    totalUnit: number;
  }[];
  branchName: string;
  total: number;
  controlNumber: string;
  generationCode: string;
  subTotal: number;
  box: number;
  selloRecibido: string;
  date: string;
  time: string;
  customer: string;
  employeeName: string;
  subTotalConIvaIncluido: number;
  typeDte: string;
  vuelto: string | number;
  pagaCon: string | number;
  observaciones: string;
  nextPayment: string;
  cash: number;
  periodo: number;
  plazo: string;
  typeOperation: string;
}
  const enviar = () => {
        const examplePrintSale: PrintSaleDTO = {
      transmitterId: 35,
      details: [
        { name: "Café molido 500g", quantity: 2, price: 3.5, discount: 0, totalUnit: 7.0 },
        { name: "Pan francés (unidad)", quantity: 10, price: 0.15, discount: 0, totalUnit: 1.5 },
        { name: "Leche entera 1L", quantity: 3, price: 1.25, discount: 0, totalUnit: 3.75 },
        { name: "Huevos (docena)", quantity: 1, price: 2.1, discount: 0.1, totalUnit: 2.0 },
        { name: "Arroz premium 5lb", quantity: 1, price: 3.2, discount: 0, totalUnit: 3.2 },
        { name: "Azúcar blanca 2lb", quantity: 2, price: 1.1, discount: 0, totalUnit: 2.2 },
        { name: "Aceite vegetal 900ml", quantity: 2, price: 2.4, discount: 0, totalUnit: 4.8 },
        { name: "Refresco 2L", quantity: 1, price: 1.75, discount: 0.25, totalUnit: 1.5 },
        { name: "Detergente 1kg", quantity: 1, price: 4.5, discount: 0.5, totalUnit: 4.0 },
        { name: "Jabón de baño (unidad)", quantity: 4, price: 0.65, discount: 0, totalUnit: 2.6 },
      ],
      branchName: "Sucursal Central - San Salvador",
      total: 32.55,
      controlNumber: "CN-002145",
      generationCode: "G-984A6F32BC21",
      subTotal: 30.0,
      box: 1,
      selloRecibido: "SR-000542",
      date: "2025-10-03",
      time: "14:35:21",
      customer: "Juan Pérez",
      employeeName: "María González",
      subTotalConIvaIncluido: 33.9,
      typeDte: "Factura de consumidor final",
      vuelto: 7.45,
      pagaCon: 40.0,
      observaciones: "Gracias por su compra. Verifique su cambio.",
      nextPayment: "",
      cash: 40.0,
      periodo: 0,
      plazo: "",
      typeOperation: "Contado",
    };
    console.log(examplePrintSale);
try {
    socket.emit("print-by-bluetooth", examplePrintSale)
     socket.on('connect', () => {
      console.log('✅ Conectado con ID:', socket.id);
      socket.emit('print-by-bluetooth', { texto: 'Hola desde la app 😎' });
    });
} catch (error) {
  console.log(error)
}
  }
   


  useEffect(() => {
    socket.on(
      "response-print-by-bluetooth",
      (data: { msg: string; ok: boolean; data: boolean }) => {
        if (data.msg === "MakeLogout success" && data.data === true) {
             console.log('📩 Respuesta recibida:', data);

        }
      }
    );

    return () => {
      socket.off("request-app");
    };
  }, []);

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


      <View style={{ marginBottom: 30 }} />
      <Button title="comenzar" onPress={enviar} />
    </SafeAreaView>
  );
}
