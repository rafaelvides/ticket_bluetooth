export const generate_ticket_language = async (
  //   items: ICartProductKiosk[],
  name: string,
  phone: string
) => {
  let ticketContent = "";
  ticketContent += `<C>***SI-HAM-OCCIDENTE***</C>\n\n`;
  const fechaHora = getElSalvadorDateTimeParam(new Date());
  ticketContent += `<L>Cliente: ${name}</L>\n<L>Sucursal: ${phone}</L>\n<D>---------------</D>\n`;
  ticketContent += `<L>Fecha: ${fechaHora.fecEmi}</L>\n`;
  ticketContent += `<L>Hora: ${fechaHora.horEmi}</L>\n`;
  ticketContent += "<D>----------------</D>\n";

  const productWidth = 14; // Ancho máximo para el nombre del producto
  const quantityWidth = 6; // Ancho para la cantidad
  const priceWidth = 12; // Ancho para el precio

  ticketContent += `<C>Producto      Cantidad   Precio</C>\n`;
  ticketContent += "<D>---------------</D>\n";

  //   items.forEach((item) => {
  //     const productName = item.product.name;
  //     const quantity = item.quantity.toString().padStart(quantityWidth, " ");
  //     const price = `$${item.price.toFixed(2)}`.padStart(priceWidth, " ");

  //     // Dividir el nombre del producto en varias líneas si es demasiado largo
  //     const maxWidth = productWidth;
  //     const lines = productName.match(new RegExp(`.{1,${maxWidth}}`, "g")) || [];

  //     lines.forEach((line, index) => {
  //       if (index === 0) {
  //         // Primera línea: incluir cantidad y precio
  //         ticketContent += `<L>${line.padEnd(
  //           maxWidth,
  //           " "
  //         )}${quantity}${price}</L>\n`;
  //       } else {
  //         // Líneas siguientes: solo el nombre del producto
  //         ticketContent += `<L>${line}</L>\n`;
  //       }
  //     });
  //   });
  ticketContent += "<D>---------------</D>\n";
  //   const total = items.reduce(
  //     (acc, item) => acc + item.price * item.quantity,
  //     0
  //   );
  //   ticketContent += `<R>Total: $${total.toFixed(2)}</R>\n`;
  ticketContent += "<C>***GRACIAS POR SU COMPRA***</C>";
  return ticketContent;
};
export function getElSalvadorDateTimeParam(date: Date): {
  fecEmi: string;
  horEmi: string;
} {
  const elSalvadorTimezone = "America/El_Salvador";
  const dateOptions: Intl.DateTimeFormatOptions = {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
    timeZone: elSalvadorTimezone,
  };

  const formattedDate = new Intl.DateTimeFormat("en-US", dateOptions).format(
    date
  );

  // Split the formatted date into date and time parts
  const [datePart, timePart] = formattedDate.split(", ");

  // Split the date into its components (month, day, year)
  const [month, day, year] = datePart.split("/");

  // Reformat the date to yyyy-mm-dd format
  const formattedDatePart = `${year}-${month.padStart(2, "0")}-${day.padStart(
    2,
    "0"
  )}`;

  return { fecEmi: formattedDatePart, horEmi: timePart };
}
