import java.util.HashMap;
import java.util.Map;

/** Protocolo de texto simple, una solicitud por linea UTF-8 y conexion. */
public final class Mensajes {
    private Mensajes() { }

    // Cada valor se escapa para no romper el delimitador ni los registros.
    public static String crear(String... partes) {
        if (partes.length % 2 != 0) throw new IllegalArgumentException("Pares clave/valor incompletos");
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < partes.length; i += 2) {
            if (i > 0) s.append('|');
            s.append(limpiar(partes[i])).append('=').append(limpiar(partes[i + 1]));
        }
        return s.toString();
    }

    public static Map<String,String> leer(String linea) {
        if (linea == null || linea.length() > 8192) throw new IllegalArgumentException("Mensaje vacio o demasiado largo");
        Map<String,String> resultado = new HashMap<>();
        for (String parte : linea.split("\\|", -1)) {
            int p = parte.indexOf('=');
            if (p < 1) throw new IllegalArgumentException("Campo mal delimitado");
            resultado.put(parte.substring(0, p), parte.substring(p + 1));
        }
        return resultado;
    }

    public static int numero(Map<String,String> m, String campo) {
        return Integer.parseInt(m.getOrDefault(campo, "-1"));
    }

    public static long reloj(Map<String,String> m) {
        return Long.parseLong(m.getOrDefault("lamport", "-1"));
    }

    private static String limpiar(String x) {
        return x.replace('|', '_').replace('=', '_').replace(';', '_')
                .replace('\r', ' ').replace('\n', ' ');
    }
}
