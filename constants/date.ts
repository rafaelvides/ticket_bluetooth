export const formatDate = () => {
  const date = new Date();
  const day = date.getDate() > 9 ? date.getDate() : `0${date.getDate()}`;
  const month =
    date.getMonth() + 1 > 9 ? date.getMonth() + 1 : `0${date.getMonth() + 1}`;
  return `${date.getFullYear()}-${month}-${day}`;
};
export const returnDate = (date: string): string => {
  const dateTime = date;
  const dateOnly = dateTime.split(" ")[0];

  return dateOnly; //
};
export const activeTime = (startTime: string) => {
  // Convertimos la cadena a objeto Date válido
  const startDate = new Date(startTime.replace(" ", "T"));
  const now = new Date();

  // Diferencia en milisegundos
  const diffMs = now.getTime() - startDate.getTime();

  // Convertimos a unidades legibles
  const diffSeconds = Math.floor(diffMs / 1000);
  const diffMinutes = Math.floor(diffSeconds / 60);
  const diffHours = Math.floor(diffMinutes / 60);
  const diffDays = Math.floor(diffHours / 24);

  const hours = diffHours % 24;
  const minutes = diffMinutes % 60;
  const seconds = diffSeconds % 60;

  let result = "";
  if (diffDays > 0) result += `${diffDays}d `;
  if (hours > 0 || diffDays > 0) result += `${hours}h `;
  if (minutes > 0 || hours > 0 || diffDays > 0) result += `${minutes}m `;
  result += `${seconds}s`;

  return result;
};
export function getCurrentDateTime() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  const hours = String(now.getHours()).padStart(2, "0");
  const minutes = String(now.getMinutes()).padStart(2, "0");
  const seconds = String(now.getSeconds()).padStart(2, "0");
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}
