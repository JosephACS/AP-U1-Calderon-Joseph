import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;


public final class Registro {
    private final int id;
    private final Path ruta;
    private long tiempo;

    public Registro(int id, String nombreArchivo) throws IOException {
        this.id = id;
        Files.createDirectories(Path.of("logs"));
        ruta = Path.of("logs", nombreArchivo + ".log");
        tiempo = 0;
        // En una simulacion, al reiniciar el proceso se conserva monotonia de su reloj en el log.
        if (Files.exists(ruta)) {
            for (String linea : Files.readAllLines(ruta, StandardCharsets.UTF_8)) {
                int sep = linea.indexOf(';');
                if (sep > 0) {
                    try { tiempo = Math.max(tiempo, Long.parseLong(linea.substring(0, sep))); }
                    catch (NumberFormatException ignored) { }
                }
            }
        }
    }

    public synchronized long local(String evento, String detalle) {
        tiempo++;
        escribir(evento, detalle);
        return tiempo;
    }

    public synchronized long recibir(long remoto, String detalle) {
        tiempo = Math.max(tiempo, Math.max(0, remoto)) + 1;
        escribir("RECEPCION", detalle);
        return tiempo;
    }

    public synchronized long enviar(String detalle) {
        tiempo++;
        escribir("ENVIO", detalle);
        return tiempo;
    }

    private void escribir(String evento, String detalle) {
        String d = detalle.replace(';', ',').replace('\r', ' ').replace('\n', ' ');
        String linea = tiempo + ";" + id + ";" + evento + ";" + d + System.lineSeparator();
        try {
            Files.writeString(ruta, linea, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir " + ruta, e);
        }
    }
}
