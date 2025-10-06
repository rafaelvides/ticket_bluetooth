import { Activity, ChartBar as BarChart3, Bluetooth, CircleCheck as CheckCircle2, Printer, TrendingUp, Wifi } from 'lucide-react-native';
import React, { useEffect, useState } from "react";
import {
    NativeModules,
    PermissionsAndroid,
    Platform,
    ScrollView,
    StyleSheet,
    Text,
    TouchableOpacity,
    View
} from "react-native";


import { Battery, Smartphone } from 'lucide-react-native';

import { BLEPrinter, IBLEPrinter } from "react-native-thermal-receipt-printer";
const { BluetoothPrinterModule } = NativeModules;
export default function home() {
    const [active, setActive] = useState(false);
    const [listPos, setListPos] = useState<IBLEPrinter[]>([]);

    useEffect(() => {
        if (Platform.OS == "android") {
            BLEPrinter.init().then(async () => {
                const print = await BLEPrinter.getDeviceList();
                setListPos(print)
            });
        }
    }, []);

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

    const onStart = async () => {
        try {
            BluetoothPrinterModule.startBackgroundService()
                .then((res: string) => {
                    console.log(res);
                })
                .catch((err: any) => {
                    console.error(err);
                });

            console.log("✅ Impresión bluetooth terminada");
            setActive(true)
        } catch (err) {
            console.log("❌ Error al imprimir por Bluetooth:", err);
        }
    };

    const onFinish = async () => {
        try {
            BluetoothPrinterModule.stopBackgroundService()
                .then((res: string) => {
                    console.log(res);
                })
                .catch((err: any) => {
                    console.error(err);
                });

            console.log("✅ Impresión bluetooth terminada");
                        setActive(false)
        } catch (err) {
            console.log("❌ Error al imprimir por Bluetooth:", err);
        }
    };
   const ondetail = async () => {
        try {
            BluetoothPrinterModule.getPrinterFullInfo()
                .then((res: string) => {
                    console.log(res);
                })
                .catch((err: any) => {
                    console.error(err);
                });

            console.log("✅ Impresión bluetooth terminada");
                        setActive(false)
        } catch (err) {
            console.log("❌ Error al imprimir por Bluetooth:", err);
        }
    };

    const printerInfo = {
        model: 'Epson TM-T20III',
        serialNumber: 'EPT2023-4567',
        status: 'Listo para imprimir',
        paperLevel: 78,
        temperature: '32°C',
        uptime: '3h 24min',
    };
    

    return (
        <ScrollView style={styles.container}>
            <View style={styles.content}>
                <View style={styles.dashboardHeader}>
                    <View>
                        <Text style={styles.welcome}>Detalles</Text>
                        <Text style={styles.headerTitle}>Sistema de Impresión</Text>
                    </View>
                    <View style={[styles.statusBadge, active && styles.statusBadgeActive]}>
                        <Text style={[styles.statusBadgeText, active && styles.statusBadgeTextActive]}>
                            {active ? 'ACTIVO' : 'INACTIVO'}
                        </Text>
                    </View>
                </View>

                <View style={styles.statsGrid}>
                    <View style={styles.statCard}>
                        <View style={styles.statHeader}>
                            <View style={styles.statIconWrapper}>
                                <TrendingUp size={20} color="#10B981" />
                            </View>
                            <Text style={styles.statValue}>100%</Text>
                        </View>
                        <Text style={styles.statLabel}>Disponibilidad</Text>
                        <View style={styles.statBar}>
                            <View style={[styles.statProgress, { width: '100%', backgroundColor: '#10B981' }]} />
                        </View>
                    </View>

                    <View style={styles.statCard}>
                        <View style={styles.statHeader}>
                            <View style={styles.statIconWrapper}>
                                <Activity size={20} color="#3B82F6" />
                            </View>
                            <Text style={styles.statValue}>{active ? '98%' : '0%'}</Text>
                        </View>
                        <Text style={styles.statLabel}>Rendimiento</Text>
                        <View style={styles.statBar}>
                            <View style={[styles.statProgress, { width: active ? '98%' : '0%', backgroundColor: '#3B82F6' }]} />
                        </View>
                    </View>

                    <View style={styles.statCard}>
                        <View style={styles.statHeader}>
                            <View style={styles.statIconWrapper}>
                                <BarChart3 size={20} color="#8B5CF6" />
                            </View>
                            <Text style={styles.statValue}>{active ? '24' : '0'}</Text>
                        </View>
                        <Text style={styles.statLabel}>Impresiones</Text>
                        <View style={styles.statBar}>
                            <View style={[styles.statProgress, { width: active ? '75%' : '0%', backgroundColor: '#8B5CF6' }]} />
                        </View>
                    </View>
                </View>

                <View style={styles.servicesPanel}>
                    <Text style={styles.panelTitle}>Estado de Servicios</Text>

                    <View style={styles.serviceRow}>
                        <View style={styles.serviceLeft}>
                            <View style={[styles.serviceIndicator, active && styles.serviceIndicatorActive]} />
                            <Bluetooth size={24} color={active ? '#3B82F6' : '#9CA3AF'} />
                            <View style={styles.serviceInfo}>
                                <Text style={styles.serviceName}>Bluetooth</Text>
                                <Text style={styles.serviceStatus}>
                                    {active ? 'Conectado' : 'Desconectado'}
                                </Text>
                            </View>
                        </View>
                        {active && <CheckCircle2 size={22} color="#10B981" />}
                    </View>

                    <View style={styles.serviceDivider} />

                    <View style={styles.serviceRow}>
                        <View style={styles.serviceLeft}>
                            <View style={[styles.serviceIndicator, active && styles.serviceIndicatorActive]} />
                            <Activity size={24} color={active ? '#8B5CF6' : '#9CA3AF'} />
                            <View style={styles.serviceInfo}>
                                <Text style={styles.serviceName}>Servicio en 2do Plano</Text>
                                <Text style={styles.serviceStatus}>
                                    {active ? 'En ejecución' : 'Detenido'}
                                </Text>
                            </View>
                        </View>
                        {active && <CheckCircle2 size={22} color="#10B981" />}
                    </View>
                </View>

                <View style={styles.devicesPanel}>
                    <Text style={styles.panelTitle}>Dispositivos Sincronizados</Text>

                    {listPos.map((device, index) => (
                        <View key={index}>
                            {index > 0 && <View style={styles.deviceDivider} />}
                            <View style={styles.deviceRow}>
                                <View style={styles.deviceIcon}>
                                    <Smartphone size={20} color="#3B82F6" />
                                </View>
                                <View style={styles.deviceInfo}>
                                    <Text style={styles.deviceName}>{device.device_name}</Text>
                                    <Text style={styles.deviceType}>{"Impresora"} • {"Disponible"}</Text>
                                </View>
                                <View style={styles.signalStrength}>
                                    <Wifi size={16} color={81 > 80 ? '#10B981' : '#F59E0B'} />
                                    <Text style={styles.signalText}>{100}%</Text>
                                </View>
                            </View>
                        </View>
                    ))}
                </View>

                {active && (
                    <View style={styles.printerInfoPanel}>
                        <View style={styles.printerHeader}>
                            <View style={styles.printerIconLarge}>
                                <Printer size={28} color="#FFFFFF" />
                            </View>
                            <View style={styles.printerHeaderText}>
                                <Text style={styles.printerModel}>{printerInfo.model}</Text>
                                <Text style={styles.printerSerial}>S/N: {printerInfo.serialNumber}</Text>
                            </View>
                            <View style={styles.printerStatusBadge}>
                                <Text style={styles.printerStatusText}>ACTIVA</Text>
                            </View>
                        </View>

                        <View style={styles.printerMetrics}>
                            <View style={styles.printerMetricItem}>
                                <Text style={styles.metricLabel}>Estado</Text>
                                <Text style={styles.metricValue}>{printerInfo.status}</Text>
                            </View>

                            <View style={styles.printerMetricDivider} />

                            <View style={styles.printerMetricItem}>
                                <View style={styles.metricRow}>
                                    <Battery size={18} color="#10B981" />
                                    <Text style={styles.metricLabel}>Nivel de Bateria</Text>
                                </View>
                                <View style={styles.paperLevelBar}>
                                    <View style={[styles.paperLevelProgress, { width: `${printerInfo.paperLevel}%` }]} />
                                </View>
                                <Text style={styles.metricValue}>{printerInfo.paperLevel}%</Text>
                            </View>

                            <View style={styles.printerMetricDivider} />

                            <View style={styles.printerMetricRow}>
                                <View style={styles.printerMetricItem}>
                                    <Text style={styles.metricLabel}>Temperatura</Text>
                                    <Text style={styles.metricValueSmall}>{printerInfo.temperature}</Text>
                                </View>

                                <View style={styles.printerMetricItem}>
                                    <Text style={styles.metricLabel}>Tiempo Activo</Text>
                                    <Text style={styles.metricValueSmall}>{printerInfo.uptime}</Text>
                                </View>
                            </View>
                        </View>
                    </View>
                )}

                <View style={styles.controlPanel}>
                    <TouchableOpacity
                        style={[styles.controlButton, active && styles.controlButtonActive]}
                        onPress={() => {
                            ondetail()
                        }}
                    >
                        <Text style={[styles.controlButtonText, active && styles.controlButtonTextActive]}>
                            {active ? 'Desactivar Sistema' : 'Activar Sistema'}
                        </Text>
                    </TouchableOpacity>

                    {active && (
                        <TouchableOpacity style={styles.printButton} onPress={onPrintBluetooth}>
                            <Printer size={22} color="#FFFFFF" />
                            <Text style={styles.printButtonText}>Imprimir Ticket de Prueba</Text>
                        </TouchableOpacity>
                    )}
                </View>

                <View style={styles.alertBox}>
                    <View style={styles.alertIcon}>
                        <Text style={styles.alertIconText}>i</Text>
                    </View>
                    <Text style={styles.alertText}>
                        El sistema verifica automáticamente los permisos de Bluetooth antes de iniciar el servicio.
                    </Text>
                </View>
            </View>
        </ScrollView>
    );
}
const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: '#F8FAFC',
        marginBottom: 35
    },
    content: {
        padding: 20,
        paddingTop: 60,
    },
    dashboardHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        marginBottom: 28,
    },
    welcome: {
        fontSize: 14,
        color: '#64748B',
        marginBottom: 4,
    },
    headerTitle: {
        fontSize: 26,
        fontWeight: '700',
        color: '#0F172A',
    },
    statusBadge: {
        backgroundColor: '#F1F5F9',
        paddingHorizontal: 12,
        paddingVertical: 6,
        borderRadius: 6,
    },
    statusBadgeActive: {
        backgroundColor: '#DCFCE7',
    },
    statusBadgeText: {
        fontSize: 12,
        fontWeight: '700',
        color: '#64748B',
        letterSpacing: 0.5,
    },
    statusBadgeTextActive: {
        color: '#16A34A',
    },
    statsGrid: {
        flexDirection: 'row',
        gap: 12,
        marginBottom: 24,
    },
    statCard: {
        flex: 1,
        backgroundColor: '#FFFFFF',
        borderRadius: 12,
        padding: 16,
        borderWidth: 1,
        borderColor: '#E2E8F0',
    },
    statHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        marginBottom: 8,
    },
    statIconWrapper: {
        width: 32,
        height: 32,
        borderRadius: 8,
        backgroundColor: '#F8FAFC',
        justifyContent: 'center',
        alignItems: 'center',
    },
    statValue: {
        fontSize: 20,
        fontWeight: '700',
        color: '#0F172A',
    },
    statLabel: {
        fontSize: 12,
        color: '#64748B',
        marginBottom: 8,
    },
    statBar: {
        height: 4,
        backgroundColor: '#F1F5F9',
        borderRadius: 2,
        overflow: 'hidden',
    },
    statProgress: {
        height: '100%',
        borderRadius: 2,
    },
    servicesPanel: {
        backgroundColor: '#FFFFFF',
        borderRadius: 16,
        padding: 20,
        marginBottom: 20,
        borderWidth: 1,
        borderColor: '#E2E8F0',
    },
    panelTitle: {
        fontSize: 16,
        fontWeight: '700',
        color: '#0F172A',
        marginBottom: 20,
    },
    serviceRow: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
    },
    serviceLeft: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 12,
        flex: 1,
    },
    serviceIndicator: {
        width: 8,
        height: 8,
        borderRadius: 4,
        backgroundColor: '#CBD5E1',
    },
    serviceIndicatorActive: {
        backgroundColor: '#10B981',
    },
    serviceInfo: {
        flex: 1,
    },
    serviceName: {
        fontSize: 15,
        fontWeight: '600',
        color: '#0F172A',
        marginBottom: 2,
    },
    serviceStatus: {
        fontSize: 13,
        color: '#64748B',
    },
    serviceDivider: {
        height: 1,
        backgroundColor: '#F1F5F9',
        marginVertical: 16,
    },
    controlPanel: {
        gap: 12,
        marginBottom: 20,
    },
    controlButton: {
        backgroundColor: '#3B82F6',
        borderRadius: 12,
        paddingVertical: 16,
        alignItems: 'center',
    },
    controlButtonActive: {
        backgroundColor: '#EF4444',
    },
    controlButtonText: {
        fontSize: 16,
        fontWeight: '700',
        color: '#FFFFFF',
    },
    controlButtonTextActive: {
        color: '#FFFFFF',
    },
    printButton: {
        backgroundColor: '#10B981',
        borderRadius: 12,
        paddingVertical: 16,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 10,
    },
    printButtonText: {
        fontSize: 15,
        fontWeight: '600',
        color: '#FFFFFF',
    },
    alertBox: {
        backgroundColor: '#EFF6FF',
        borderRadius: 12,
        padding: 16,
        flexDirection: 'row',
        gap: 12,
        borderLeftWidth: 4,
        borderLeftColor: '#3B82F6',
    },
    alertIcon: {
        width: 24,
        height: 24,
        borderRadius: 12,
        backgroundColor: '#3B82F6',
        justifyContent: 'center',
        alignItems: 'center',
    },
    alertIconText: {
        fontSize: 14,
        fontWeight: '700',
        color: '#FFFFFF',
    },
    alertText: {
        flex: 1,
        fontSize: 13,
        color: '#1E40AF',
        lineHeight: 20,
    },
    devicesPanel: {
        backgroundColor: '#FFFFFF',
        borderRadius: 16,
        padding: 20,
        marginBottom: 20,
        borderWidth: 1,
        borderColor: '#E2E8F0',
    },
    deviceRow: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 12,
    },
    deviceIcon: {
        width: 44,
        height: 44,
        borderRadius: 12,
        backgroundColor: '#EFF6FF',
        justifyContent: 'center',
        alignItems: 'center',
    },
    deviceInfo: {
        flex: 1,
    },
    deviceName: {
        fontSize: 15,
        fontWeight: '600',
        color: '#0F172A',
        marginBottom: 2,
    },
    deviceType: {
        fontSize: 12,
        color: '#64748B',
    },
    signalStrength: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 6,
        backgroundColor: '#F8FAFC',
        paddingHorizontal: 10,
        paddingVertical: 6,
        borderRadius: 8,
    },
    signalText: {
        fontSize: 12,
        fontWeight: '600',
        color: '#64748B',
    },
    deviceDivider: {
        height: 1,
        backgroundColor: '#F1F5F9',
        marginVertical: 16,
    },
    printerInfoPanel: {
        backgroundColor: '#F8FAFC',
        borderRadius: 16,
        padding: 20,
        marginBottom: 20,
        borderWidth: 2,
        borderColor: '#10B981',
    },
    printerHeader: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 12,
        marginBottom: 20,
        paddingBottom: 20,
        borderBottomWidth: 1,
        borderBottomColor: '#E2E8F0',
    },
    printerIconLarge: {
        width: 56,
        height: 56,
        borderRadius: 14,
        backgroundColor: '#10B981',
        justifyContent: 'center',
        alignItems: 'center',
    },
    printerHeaderText: {
        flex: 1,
    },
    printerModel: {
        fontSize: 17,
        fontWeight: '700',
        color: '#0F172A',
        marginBottom: 4,
    },
    printerSerial: {
        fontSize: 12,
        color: '#64748B',
        fontFamily: 'monospace',
    },
    printerStatusBadge: {
        backgroundColor: '#DCFCE7',
        paddingHorizontal: 12,
        paddingVertical: 6,
        borderRadius: 6,
    },
    printerStatusText: {
        fontSize: 11,
        fontWeight: '700',
        color: '#16A34A',
        letterSpacing: 0.5,
    },
    printerMetrics: {
        gap: 16,
    },
    printerMetricItem: {
        gap: 8,
    },
    metricRow: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 8,
    },
    metricLabel: {
        fontSize: 12,
        fontWeight: '600',
        color: '#64748B',
        textTransform: 'uppercase',
        letterSpacing: 0.5,
    },
    metricValue: {
        fontSize: 15,
        fontWeight: '600',
        color: '#0F172A',
    },
    metricValueSmall: {
        fontSize: 14,
        fontWeight: '600',
        color: '#0F172A',
    },
    paperLevelBar: {
        height: 8,
        backgroundColor: '#E2E8F0',
        borderRadius: 4,
        overflow: 'hidden',
    },
    paperLevelProgress: {
        height: '100%',
        backgroundColor: '#10B981',
        borderRadius: 4,
    },
    printerMetricDivider: {
        height: 1,
        backgroundColor: '#E2E8F0',
    },
    printerMetricRow: {
        flexDirection: 'row',
        gap: 20,
    },
});