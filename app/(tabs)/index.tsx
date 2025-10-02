import React, { useEffect } from "react";
import { Button, View } from "react-native";
import { BLEPrinter } from "react-native-thermal-receipt-printer";

export default function App() {
  useEffect(() => {
    BLEPrinter.init().then(() => {
      BLEPrinter.getDeviceList().then(console.log); // lista de impresoras disponibles
    });
  }, []);

  const printTicket = () => {
    BLEPrinter.printText("Ticket de prueba");
    BLEPrinter.printText("Ticket de prueba 2");
    BLEPrinter.printText("Ticket de prueba 3");
    BLEPrinter.printText("Ticket de prueba 4");
  };

  return (
    <View style={{ marginTop: 50 }}>
      <Button title="Imprimir Ticket" onPress={printTicket} />
    </View>
  );
}
