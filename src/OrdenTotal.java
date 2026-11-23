import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class OrdenTotal {
    private record Evento(long lamport, int proceso, String texto) { }

    public static void main(String[] args) throws Exception {
        List<Evento> eventos = new ArrayList<>();
        for (String nombre : List.of("Coordinador", "P1", "P2", "P3")) {
            Path archivo = Path.of("logs", nombre + ".log");
            if (!Files.exists(archivo)) {
                System.err.println("AVISO: falta " + archivo);
                continue;
            }
            for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
                String[] partes = linea.split(";", 4);
                if (partes.length != 4) continue;
                try { eventos.add(new Evento(Long.parseLong(partes[0]), Integer.parseInt(partes[1]), linea)); }
                catch (NumberFormatException ignored) { }
            }
        }
        eventos.sort(Comparator.comparingLong(Evento::lamport).thenComparingInt(Evento::proceso));
        Path salida = Path.of("logs", "orden_total.log");
        Files.createDirectories(salida.getParent());
        List<String> lineas = eventos.stream().map(Evento::texto).toList();
        Files.write(salida, lineas, StandardCharsets.UTF_8);
        System.out.printf("[OrdenTotal] %d eventos reunidos -> %s%n", eventos.size(), salida);
        System.out.println("Busque el mismo 'id=' en ENVIO de una puerta y RECEPCION del coordinador;");
        System.out.println("o ENVIO del coordinador y RECEPCION de una puerta: la marca debe ser creciente.");
        for (int i = 0; i < Math.min(8, lineas.size()); i++) System.out.println(lineas.get(i));
    }
}
