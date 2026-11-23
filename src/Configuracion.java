import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;


public final class Configuracion {
    private final Properties p = new Properties();

    private Configuracion() { }

    public static Configuracion cargar() throws IOException {
        Configuracion c = new Configuracion();
        try (InputStream in = Files.newInputStream(Path.of("config.properties"))) {
            c.p.load(in);
        }
        return c;
    }

    public String host() { return obligatorio("host"); }
    public int puerto() { return Integer.parseInt(obligatorio("puerto.coordinador")); }
    public String token() { return obligatorio("token"); }
    public int aforo() { return Integer.parseInt(obligatorio("aforo")); }
    public int timeoutMs() { return Integer.parseInt(obligatorio("socket.timeout.ms")); }
    public int heartbeatMs() { return Integer.parseInt(obligatorio("heartbeat.ms")); }
    public int caidaMs() { return Integer.parseInt(obligatorio("caida.ms")); }

    private String obligatorio(String clave) {
        String v = p.getProperty(clave);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Falta " + clave + " en config.properties");
        return v.trim();
    }
}
