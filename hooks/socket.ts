import { io } from "socket.io-client";

export const createSocketCutom = () => {
  const socket = io("ws://localhost:3000/sales-gateway", {
    transports: ["websocket"],
  });

  return socket;
};
