import { create } from "zustand";

interface IInfoPrinter {
  name: string;
  model: string;
  address_ip: string;
}
interface IInfoPrinterStore {
  infoPrinter: IInfoPrinter;
  setInfoPrinter: (infoPrinter: IInfoPrinter) => void;
  cleanInfoPrinter: () => void;
}

export const useInfoPrinterStore = create<IInfoPrinterStore>((set) => ({
  infoPrinter: {
    name: "",
    model: "",
    address_ip: "",
  },
  setInfoPrinter: (infoPrinter) => set({ infoPrinter }),
  cleanInfoPrinter: () =>
    set({ infoPrinter: { name: "", model: "", address_ip: "" } }),
}));
