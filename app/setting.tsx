import { getCurrentDateTime } from "@/constants/date";
import { useInfoPrinterStore } from "@/store/info_printer.store";
import { useFocusEffect } from "expo-router";
import {
  ChevronDown,
  ChevronUp,
  Edit3,
  Plus,
  Save,
  Trash2,
  X,
} from "lucide-react-native";
import { useCallback, useState } from "react";
import {
  Alert,
  NativeModules,
  Platform,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  ToastAndroid,
  TouchableOpacity,
  View,
} from "react-native";
const { BluetoothPrinterModule } = NativeModules;

interface PrinterConfig {
  model: string;
  name: string;
  socketUrl: string;
  doc: string;
  ticket: string;
  address_ip: string;
  date: string;
  id: string;
  enviroment: string;
}

export default function PrinterConfig2List() {
  const [printers, setPrinters] = useState<PrinterConfig[]>([]);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editData, setEditData] = useState<PrinterConfig | null>(null);
  const { infoPrinter, cleanInfoPrinter } = useInfoPrinterStore();

  useFocusEffect(
    useCallback(() => {
      if (Platform.OS == "android") {
        ondetailDB();
      }
    }, [])
  );

  const handleExpand = (id: string) => {
    setExpandedId(expandedId === id ? null : id);
    setEditingId(null);
  };

  const handleEdit = (printer: PrinterConfig) => {
    setEditingId(printer.id);
    setEditData({ ...printer });
  };
  console.log("id edit", editingId);
  const handleSave = () => {
    if (!editData) return;
    if (editData.doc === "") return;
    if (editData.address_ip === "") return;
    if (editData.socketUrl === "") return;
    if (editingId === null || editingId === "") {
      BluetoothPrinterModule.savePrinters(
        editData.model,
        editData.name,
        editData.ticket,
        editData.address_ip,
        editData.socketUrl,
        editData.doc,
        editData.enviroment
      )
        .then(() => {
          ToastAndroid.show(
            "Impresora guardada correctamente",
            ToastAndroid.CENTER
          );
          ondetailDB();
          cleanInfoPrinter();
        })
        .catch(() => {
          ToastAndroid.show(
            "Ocurrio un problema al momento de guardar la impresora",
            ToastAndroid.CENTER
          );
        });
    } else {
      console.log("dikan", editData.enviroment);
      BluetoothPrinterModule.updatePrinter(
        Number(editingId), // editingId,
        editData.model,
        editData.name,
        String(editData.ticket), // editData.ticket,
        editData.address_ip,
        editData.socketUrl,
        editData.doc,
        editData.enviroment
      )
        .then(() => {
          ToastAndroid.show(
            "Impresora actualizada correctamente",
            ToastAndroid.CENTER
          );
          ondetailDB();
        })
        .catch(() => {
          ToastAndroid.show(
            "Ocurrio un problema al momento de actualizar la impresora",
            ToastAndroid.CENTER
          );
        });
    }

    setPrinters(printers.map((p) => (p.id === editData.id ? editData : p)));
    setEditingId(null);
    setEditData(null);
  };

  const handleCancel = () => {
    setEditingId(null);
    setEditData(null);
    cleanInfoPrinter();
  };

  const handleAdd = () => {
    const fecha = getCurrentDateTime();
    const newPrinter: PrinterConfig = {
      id: "",
      name: infoPrinter.name ?? "",
      socketUrl: "",
      address_ip: infoPrinter.address_ip ?? "",
      doc: "",
      enviroment: "01",
      ticket: "0",
      date: fecha,
      model: infoPrinter.model ?? "",
    };
    setPrinters([newPrinter, ...printers]);
    setExpandedId(newPrinter.id);
    setEditingId(newPrinter.id);
    setEditData(newPrinter);
  };
  const ondetailDB = async () => {
    try {
      BluetoothPrinterModule.getAllPrinterRecords()
        .then((res: any) => {
          console.log("como se guardo la impresora", res);
          setPrinters(res);
        })
        .catch(() => {
          ToastAndroid.show(
            "Ocurrio un problema al momento de detener los detalles",
            ToastAndroid.CENTER
          );
        });
    } catch (err) {
      ToastAndroid.show(
        "Ocurrio un problema al momento de detener los detalles",
        ToastAndroid.CENTER
      );
    }
  };
  const handleDelete = (id: string) => {
    Alert.alert("Confirmar", "¿Eliminar esta configuración?", [
      { text: "Cancelar", style: "cancel" },
      {
        text: "Eliminar",
        style: "destructive",
        onPress: () => {
          BluetoothPrinterModule.deletePrinterByIdReact(Number(id))
            .then(() => {
              ToastAndroid.show(
                "Impresora eliminada correctamente",
                ToastAndroid.CENTER
              );
              ondetailDB();
            })
            .catch(() => {
              ToastAndroid.show(
                "Ocurrio un problema al momento de eliminar la impresora",
                ToastAndroid.CENTER
              );
            });
          setPrinters(printers.filter((p) => p.id !== id));
          if (expandedId === id) setExpandedId(null);
        },
      },
    ]);
  };

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Informacion de impresoras</Text>
        <TouchableOpacity style={styles.addButton} onPress={handleAdd}>
          <Plus color="#FFF" size={20} />
          <Text style={styles.addButtonText}>Agregar</Text>
        </TouchableOpacity>
      </View>

      <ScrollView
        style={styles.scrollView}
        showsVerticalScrollIndicator={false}
      >
        {printers.map((printer) => {
          const isExpanded = expandedId === printer.id;
          const isEditing = editingId === printer.id;
          const currentData = isEditing && editData ? editData : printer;

          return (
            <View key={printer.id} style={styles.listItem}>
              {/* Header colapsable */}
              <TouchableOpacity
                style={styles.listHeader}
                onPress={() => handleExpand(printer.id)}
              >
                <View style={styles.listHeaderLeft}>
                  <View
                    style={[
                      styles.statusDot,
                      printer.enviroment === "01"
                        ? styles.statusProd
                        : styles.statusTest,
                    ]}
                  />
                  <Text style={styles.listTitle}>{printer.name}</Text>
                </View>
                <View style={styles.listHeaderRight}>
                  <Text style={styles.ipText}>{printer.address_ip}</Text>
                  {isExpanded ? (
                    <ChevronUp color="#64748B" size={20} />
                  ) : (
                    <ChevronDown color="#64748B" size={20} />
                  )}
                </View>
              </TouchableOpacity>

              {/* Contenido expandido */}
              {isExpanded && (
                <View style={styles.expandedContent}>
                  {isEditing ? (
                    // Modo edición
                    <>
                      <View style={styles.formRow}>
                        <Text style={styles.formLabel}>Nombre</Text>
                        <TextInput
                          style={styles.formInput}
                          value={currentData.name}
                          onChangeText={(text) =>
                            setEditData({ ...currentData, name: text })
                          }
                        />
                      </View>
                      <View style={styles.formRow}>
                        <Text style={styles.formLabel}>Socket URL</Text>
                        <TextInput
                          style={styles.formInput}
                          value={currentData.socketUrl}
                          onChangeText={(text) =>
                            setEditData({ ...currentData, socketUrl: text })
                          }
                        />
                      </View>
                      <View style={styles.formRow}>
                        <Text style={styles.formLabel}>IP Address</Text>
                        <TextInput
                          style={styles.formInput}
                          value={currentData.address_ip}
                          onChangeText={(text) =>
                            setEditData({ ...currentData, address_ip: text })
                          }
                          keyboardType="numeric"
                        />
                      </View>
                      <View style={styles.formRow}>
                        <Text style={styles.formLabel}>Emisor ID</Text>
                        <TextInput
                          style={styles.formInput}
                          value={currentData.doc}
                          onChangeText={(text) =>
                            setEditData({ ...currentData, doc: text })
                          }
                        />
                      </View>
                      <View style={styles.formRow}>
                        <Text style={styles.formLabel}>Ambiente</Text>
                        <View style={styles.switchRow}>
                          <Text style={styles.switchText}>Pruebas</Text>
                          <Switch
                            value={currentData.enviroment === "01"}
                            onValueChange={(value) =>
                              setEditData({
                                ...currentData,
                                enviroment: value ? "01" : "00",
                              })
                            }
                            trackColor={{ false: "#CBD5E1", true: "#a4d0d1ff" }}
                          />
                          <Text style={styles.switchText}>Producción</Text>
                        </View>
                      </View>
                      <View style={styles.editActions}>
                        <TouchableOpacity
                          style={styles.cancelBtn}
                          onPress={handleCancel}
                        >
                          <X color="#EF4444" size={18} />
                          <Text style={styles.cancelBtnText}>Cancelar</Text>
                        </TouchableOpacity>
                        <TouchableOpacity
                          style={styles.saveBtn}
                          onPress={handleSave}
                        >
                          <Save color="#FFF" size={18} />
                          <Text style={styles.saveBtnText}>Guardar</Text>
                        </TouchableOpacity>
                      </View>
                    </>
                  ) : (
                    <>
                      <View style={styles.detailRow}>
                        <Text style={styles.detailLabel}>Socket URL:</Text>
                        <Text style={styles.detailValue}>
                          {" "}
                          {printer.socketUrl}
                        </Text>
                      </View>
                      <View style={styles.detailRow}>
                        <Text style={styles.detailLabel}>Emisor ID:</Text>
                        <Text style={styles.detailValue}>{printer.doc}</Text>
                      </View>
                      <View style={styles.detailRow}>
                        <Text style={styles.detailLabel}>Ambiente:</Text>
                        <Text style={styles.detailValue}>
                          {printer.enviroment === "01"
                            ? "Producción (01)"
                            : "Pruebas (00)"}
                        </Text>
                      </View>
                      <View style={styles.actions}>
                        <TouchableOpacity
                          style={styles.editButton}
                          onPress={() => handleEdit(printer)}
                        >
                          <Edit3 color="#3B82F6" size={18} />
                          <Text style={styles.editButtonText}>Editar</Text>
                        </TouchableOpacity>
                        <TouchableOpacity
                          style={styles.deleteButton}
                          onPress={() => handleDelete(printer.id)}
                        >
                          <Trash2 color="#EF4444" size={18} />
                          <Text style={styles.deleteButtonText}>Eliminar</Text>
                        </TouchableOpacity>
                      </View>
                    </>
                  )}
                </View>
              )}
            </View>
          );
        })}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#F8FAFC",
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    padding: 20,
    backgroundColor: "#FFF",
    borderBottomWidth: 1,
    borderBottomColor: "#E2E8F0",
  },
  headerTitle: {
    fontSize: 17,
    fontWeight: "700",
    color: "#1E293B",
  },
  addButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    backgroundColor: "#3B82F6",
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 12,
  },
  addButtonText: {
    color: "#FFF",
    fontSize: 14,
    fontWeight: "600",
  },
  scrollView: {
    flex: 1,
  },
  listItem: {
    backgroundColor: "#FFF",
    marginHorizontal: 16,
    marginTop: 12,
    borderRadius: 12,
    overflow: "hidden",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 2,
  },
  listHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    padding: 16,
  },
  listHeaderLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    flex: 1,
  },
  statusDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  statusProd: {
    backgroundColor: "#10B981",
  },
  statusTest: {
    backgroundColor: "#10B981",
  },
  listTitle: {
    fontSize: 16,
    fontWeight: "600",
    color: "#1E293B",
    flex: 1,
  },
  listHeaderRight: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  ipText: {
    fontSize: 13,
    color: "#64748B",
    fontWeight: "500",
  },
  expandedContent: {
    padding: 16,
    paddingTop: 0,
    borderTopWidth: 1,
    borderTopColor: "#F1F5F9",
  },
  detailRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingVertical: 8,
  },
  detailLabel: {
    fontSize: 14,
    color: "#64748B",
    fontWeight: "500",
  },
  detailValue: {
    fontSize: 14,
    color: "#1E293B",
    fontWeight: "600",
  },
  actions: {
    flexDirection: "row",
    gap: 12,
    marginTop: 16,
  },
  editButton: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    backgroundColor: "#EFF6FF",
    padding: 12,
    borderRadius: 8,
  },
  editButtonText: {
    color: "#3B82F6",
    fontSize: 14,
    fontWeight: "600",
  },
  deleteButton: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    backgroundColor: "#FEE2E2",
    padding: 12,
    borderRadius: 8,
  },
  deleteButtonText: {
    color: "#EF4444",
    fontSize: 14,
    fontWeight: "600",
  },
  formRow: {
    marginBottom: 16,
  },
  formLabel: {
    fontSize: 13,
    fontWeight: "600",
    color: "#475569",
    marginBottom: 6,
  },
  formInput: {
    backgroundColor: "#F8FAFC",
    borderWidth: 1,
    borderColor: "#E2E8F0",
    borderRadius: 8,
    padding: 12,
    fontSize: 14,
    color: "#1E293B",
  },
  switchRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    backgroundColor: "#F8FAFC",
    padding: 10,
    borderRadius: 8,
  },
  switchText: {
    fontSize: 13,
    color: "#64748B",
    fontWeight: "500",
  },
  editActions: {
    flexDirection: "row",
    gap: 12,
    marginTop: 16,
  },
  cancelBtn: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    backgroundColor: "#FEF2F2",
    padding: 12,
    borderRadius: 8,
  },
  cancelBtnText: {
    color: "#EF4444",
    fontSize: 14,
    fontWeight: "600",
  },
  saveBtn: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    backgroundColor: "#3B82F6",
    padding: 12,
    borderRadius: 8,
  },
  saveBtnText: {
    color: "#FFF",
    fontSize: 14,
    fontWeight: "600",
  },
});
