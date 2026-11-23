import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Puerta {
    private final int id;
    private final Configuracion config;
    private final Registro log;
    private final ScheduledExecutorService latidos = Executors.newSingleThreadScheduledExecutor();

    private Puerta(int id) throws IOException {
        if (id < 1 || id > 3) throw new IllegalArgumentException("Puerta: use 1, 2 o 3");
        this.id = id;
        config = Configuracion.cargar();
        log = new Registro(id, "P" + id);
    }

    public static void main(String[] args) throws Exception {
        int id = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        new Puerta(id).iniciar();
    }

    private void iniciar() throws IOException {
        String nombre = switch (id) { case 1 -> "Norte"; case 2 -> "Sur"; default -> "Este"; };
        System.out.printf("[P%d %s] Lista. Ordenes: entrar, salir, estado, token_malo, ayuda, fin%n", id, nombre);
        log.local("INICIO", "P" + id + " " + nombre);
        latidos.scheduleAtFixedRate(() -> solicitar("LATIDO", false), 0, config.heartbeatMs(), TimeUnit.MILLISECONDS);
        try (BufferedReader consola = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String cmd;
            while ((cmd = consola.readLine()) != null) {
                switch (cmd.trim().toLowerCase()) {
                    case "entrar" -> solicitar("ENTRAR", false);
                    case "salir" -> solicitar("SALIR", false);
                    case "estado" -> solicitar("ESTADO", false);
                    case "token_malo" -> solicitar("ENTRAR", true);
                    case "ayuda" -> System.out.println("entrar | salir | estado | token_malo (solo E2) | fin");
                    case "fin", "salir_programa", "exit" -> { return; }
                    case "" -> { }
                    default -> System.out.println("Orden no reconocida. Escriba ayuda.");
                }
            }
        } finally {
            latidos.shutdownNow();
            log.local("CIERRE", "P" + id);
        }
    }

    private void solicitar(String tipo, boolean tokenInvalido) {
        String idSolicitud = "P" + id + "-" + UUID.randomUUID();
        long marca = log.enviar("id=" + idSolicitud + " tipo=" + tipo + " destino=C0");
        String token = tokenInvalido ? "TOKEN_MALO_E2" : config.token();
        String envio = Mensajes.crear("tipo", tipo, "token", token, "puerta", Integer.toString(id),
                "lamport", Long.toString(marca), "id", idSolicitud);
        try {
            Map<String,String> respuesta = ClienteTcp.llamar(config, envio);
            log.recibir(Mensajes.reloj(respuesta), "id=" + idSolicitud + " resultado=" + respuesta.get("resultado"));
            if (!"LATIDO".equals(tipo)) {
                System.out.printf("[P%d] %s -> %s %s | dentro=%s/%s | %s%n", id, tipo,
                        respuesta.get("resultado"), respuesta.getOrDefault("motivo", ""),
                        respuesta.getOrDefault("dentro", "?"), respuesta.getOrDefault("aforo", "?"),
                        respuesta.getOrDefault("puertas", ""));
            }
        } catch (IOException e) {
            log.local("ERROR_RED", "id=" + idSolicitud + " tipo=" + tipo + " " + e.getClass().getSimpleName());
            if (!"LATIDO".equals(tipo)) {
                System.out.printf("[P%d] NO SE AUTORIZA %s: coordinador no disponible (%s)%n", id, tipo, e.getMessage());
            }
        }
    }
}
