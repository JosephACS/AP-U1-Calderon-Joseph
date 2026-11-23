import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public final class Coordinador {
    private final Configuracion c;
    private final Registro log;
    private final AtomicInteger dentro = new AtomicInteger();
    private final ReentrantLock aforoLock = new ReentrantLock();
    private final ConcurrentHashMap<Integer, Long> ultimoLatido = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> puertaEstado = new ConcurrentHashMap<>();
    private final boolean inseguro;

    private Coordinador(Configuracion c, boolean inseguro) throws IOException {
        this.c = c;
        this.inseguro = inseguro;
        log = new Registro(0, "Coordinador");
        for (int i = 1; i <= 3; i++) puertaEstado.put(i, "NO_INICIADA");
    }

    public static void main(String[] args) throws Exception {
        boolean inseguro = false;
        for (String a : args) if (a.equalsIgnoreCase("--inseguro") || a.equalsIgnoreCase("-inseguro")) inseguro = true;
        new Coordinador(Configuracion.cargar(), inseguro).iniciar();
    }

    private void iniciar() throws IOException {
        log.local("INICIO", "coordinador modo=" + (inseguro ? "INSEGURO" : "NORMAL"));
        System.out.printf("[C0] Escuchando en %s:%d | aforo=%d | modo=%s%n", c.host(), c.puerto(), c.aforo(), inseguro ? "INSEGURO" : "NORMAL");
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(this::verificarCaidas, 1, 1, TimeUnit.SECONDS);
        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket server = new ServerSocket(c.puerto())) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.local("CIERRE", "coordinador detenido");
                monitor.shutdownNow();
                pool.shutdownNow();
            }));
            while (true) {
                Socket cliente = server.accept();
                pool.execute(() -> atender(cliente));
            }
        } finally {
            monitor.shutdownNow();
            pool.shutdownNow();
        }
    }

    private void verificarCaidas() {
        long ahora = System.currentTimeMillis();
        for (int i = 1; i <= 3; i++) {
            Long ms = ultimoLatido.get(i);
            if (ms != null && ahora - ms >= c.caidaMs()
                    && !"CAIDA".equals(puertaEstado.get(i))) {
                puertaEstado.put(i, "CAIDA");
                log.local("CAIDA", "P" + i + " sin latido " + c.caidaMs() + " ms");
                System.out.println("[C0] P" + i + " CAIDA (sin latidos)");
            }
        }
    }

    private void atender(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            socket.setSoTimeout(c.timeoutMs());

            String linea;
            while ((linea = in.readLine()) != null) {
            Map<String,String> req = Mensajes.leer(linea);
            String id = req.getOrDefault("id", "sin-id");
            String tipo = req.getOrDefault("tipo", "DESCONOCIDO");
            int puerta = Mensajes.numero(req, "puerta");
            log.recibir(Mensajes.reloj(req), "id=" + id + " tipo=" + tipo + " P" + puerta);
            String resultado, motivo = "", estados = "";
            if (!c.token().equals(req.get("token"))) {
                resultado = "RECHAZADA";
                motivo = "TOKEN_INVALIDO";
                log.local("TOKEN_INVALIDO", "id=" + id + " P" + puerta);
            } else if (puerta < 1 || puerta > 3) {
                resultado = "RECHAZADA";
                motivo = "PUERTA_INVALIDA";
            } else {
                switch (tipo) {
                    case "LATIDO" -> {
                        ultimoLatido.put(puerta, System.currentTimeMillis());
                        String anterior = puertaEstado.put(puerta, "ACTIVA");
                        if (!"ACTIVA".equals(anterior)) {
                            log.local("ACTIVA", "P" + puerta + " antes=" + anterior);
                            System.out.println("[C0] P" + puerta + " ACTIVA");
                        }
                        resultado = "PERMITIDA";
                    }
                    case "ENTRAR" -> {
                        boolean entra = entrar();
                        resultado = entra ? "PERMITIDA" : "RECHAZADA";
                        motivo = entra ? "" : "AFORO_COMPLETO";
                        log.local(entra ? "ENTRADA" : "RECHAZO", "id=" + id + " P" + puerta + " dentro=" + dentro.get());
                    }
                    case "SALIR" -> {
                        boolean sale = salir();
                        resultado = sale ? "PERMITIDA" : "RECHAZADA";
                        motivo = sale ? "" : "NADIE_DENTRO";
                        log.local(sale ? "SALIDA" : "RECHAZO", "id=" + id + " P" + puerta + " dentro=" + dentro.get());
                    }
                    case "ESTADO" -> resultado = "PERMITIDA";
                    default -> {
                        resultado = "RECHAZADA";
                        motivo = "TIPO_DESCONOCIDO";
                    }
                }
            }
            if (!"LATIDO".equals(tipo)) {
                estados = "P1:" + puertaEstado.get(1) + ",P2:" + puertaEstado.get(2) + ",P3:" + puertaEstado.get(3);
                System.out.printf("[C0] %s P%d %s %s | dentro=%d/%d%n", tipo, puerta, resultado, motivo, dentro.get(), c.aforo());
            }
            long marca = log.enviar("id=" + id + " resultado=" + resultado + " motivo=" + motivo);
            out.write(Mensajes.crear("tipo", "RESPUESTA", "id", id,
                    "resultado", resultado, "motivo", motivo,
                    "dentro", Integer.toString(dentro.get()), "aforo", Integer.toString(c.aforo()),
                    "puertas", estados, "lamport", Long.toString(marca)));
            out.newLine();
            out.flush();
            }
        } catch (Exception e) {
            log.local("ERROR", e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private boolean entrar() {
        if (inseguro) {

            int antes = dentro.get();
            if (antes >= c.aforo()) return false;
            try { Thread.sleep(50); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
            dentro.set(antes + 1); // Varias entradas pueden ser autorizadas con el MISMO valor antiguo.
            return true;
        }
        aforoLock.lock();
        try {
            if (dentro.get() >= c.aforo()) return false;
            dentro.incrementAndGet();
            return true;
        } finally { aforoLock.unlock(); }
    }

    private boolean salir() {
        aforoLock.lock();
        try {
            if (dentro.get() <= 0) return false;
            dentro.decrementAndGet();
            return true;
        } finally { aforoLock.unlock(); }
    }
}
