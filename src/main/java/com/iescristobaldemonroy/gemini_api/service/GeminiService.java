package com.iescristobaldemonroy.gemini_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-3.5-flash-lite}")
    private String modelo;

    private static final int MAX_MENSAJES_HISTORIAL = 10;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Estructura interna de mensaje
    public static class MensajeHistorial {
        private final String rol;
        private final String texto;

        public MensajeHistorial(String rol, String texto) {
            this.rol = rol;
            this.texto = texto;
        }

        public String getRol() { return rol; }
        public String getTexto() { return texto; }
    }

    // Mapa thread-safe: cada ID de sesión tiene su propia lista de mensajes aislada
    private final Map<String, List<MensajeHistorial>> sesionesHistorial = new ConcurrentHashMap<>();

    private List<MensajeHistorial> obtenerHistorialSesion(String sessionId) {
        return sesionesHistorial.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    public synchronized String enviarMensaje(String sessionId, String promptUsuario) {
        List<MensajeHistorial> historial = obtenerHistorialSesion(sessionId);

        // 1. Añadimos mensaje del usuario y podamos
        historial.add(new MensajeHistorial("user", promptUsuario));
        podarHistorialSiExcedeLimite(historial);

        String respuestaTexto;
        try {
            respuestaTexto = llamarApiGoogle(historial);
        } catch (Exception e) {
            System.err.println("--> Fallo con Google (" + e.getMessage() + "). Usando fallback simulado.");
            respuestaTexto = "[Modo Simulación] Backend operativo. Registrado: \"" + promptUsuario + "\".";
        }

        // 2. Añadimos respuesta generada y podamos
        historial.add(new MensajeHistorial("model", respuestaTexto));
        podarHistorialSiExcedeLimite(historial);

        return respuestaTexto;
    }

    public synchronized void limpiarHistorial(String sessionId) {
        List<MensajeHistorial> historial = sesionesHistorial.get(sessionId);
        if (historial != null) {
            historial.clear();
        }
        System.out.println("--> Historial reiniciado para la sesión: " + sessionId);
    }

    private void podarHistorialSiExcedeLimite(List<MensajeHistorial> historial) {
        while (historial.size() > MAX_MENSAJES_HISTORIAL) {
            historial.remove(0);
        }
    }

    private String llamarApiGoogle(List<MensajeHistorial> historial) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + modelo + ":generateContent?key=" + apiKey;

        URL url = new URL(endpoint);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("x-goog-api-key", apiKey);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);

        ObjectNode rootNode = objectMapper.createObjectNode();
        ArrayNode contentsArray = rootNode.putArray("contents");

        for (MensajeHistorial m : historial) {
            ObjectNode content = contentsArray.addObject();
            content.put("role", m.getRol());
            ArrayNode parts = content.putArray("parts");
            ObjectNode part = parts.addObject();
            part.put("text", m.getTexto());
        }

        byte[] input = objectMapper.writeValueAsBytes(rootNode);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(input, 0, input.length);
        }

        int statusCode = conn.getResponseCode();
        System.out.println("--> Estado HTTP de Google: " + statusCode);

        InputStreamReader isr = (statusCode >= 200 && statusCode < 300)
                ? new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
                : new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8);

        StringBuilder responseBody = new StringBuilder();
        try (BufferedReader br = new BufferedReader(isr)) {
            String line;
            while ((line = br.readLine()) != null) {
                responseBody.append(line);
            }
        }

        if (statusCode == 200) {
            return extraerTextoConJackson(responseBody.toString());
        } else {
            throw new RuntimeException("Error Google [" + statusCode + "]: " + responseBody);
        }
    }

    private String extraerTextoConJackson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode candidates = root.path("candidates");

            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode primerCandidato = candidates.get(0);

                // 1. Comprobar si hubo un bloqueo por políticas de seguridad
                String finishReason = primerCandidato.path("finishReason").asText("");
                if ("SAFETY".equalsIgnoreCase(finishReason)) {
                    return "⚠️ **Aviso de seguridad:** La respuesta fue bloqueada por los filtros de contenido de la API de Gemini.";
                }

                // 2. Extraer el texto si la generación concluyó con normalidad
                JsonNode parts = primerCandidato.path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText();
                }

                if (!finishReason.isEmpty() && !"STOP".equalsIgnoreCase(finishReason)) {
                    return "⚠️ La respuesta finalizó con estado inusual: " + finishReason;
                }
            }

            // 3. Comprobar si la petición completa fue bloqueada a nivel de prompt
            JsonNode promptFeedback = root.path("promptFeedback");
            if (promptFeedback.has("blockReason")) {
                return "⚠️ **Petición rechazada:** " + promptFeedback.path("blockReason").asText();
            }

            return "No se encontró texto en la respuesta del modelo.";
        } catch (Exception e) {
            return "Error al procesar la estructura JSON: " + e.getMessage();
        }
    }
    // ==========================================
    // MÉTODO STREAMING (Server-Sent Events)
    // ==========================================
    public synchronized void enviarMensajeStream(String sessionId, String promptUsuario, java.util.function.Consumer<String> consumidorChunk) throws Exception {
        List<MensajeHistorial> historial = obtenerHistorialSesion(sessionId);

        // 1. Añadimos el prompt del usuario
        historial.add(new MensajeHistorial("user", promptUsuario));
        podarHistorialSiExcedeLimite(historial);

        // 2. Llamada a Google con alt=sse
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + modelo + ":streamGenerateContent?alt=sse&key=" + apiKey;

        URL url = new URL(endpoint);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("x-goog-api-key", apiKey);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);

        // Reutilizamos la misma estructura JSON
        ObjectNode rootNode = objectMapper.createObjectNode();
        ArrayNode contentsArray = rootNode.putArray("contents");
        for (MensajeHistorial m : historial) {
            ObjectNode content = contentsArray.addObject();
            content.put("role", m.getRol());
            ArrayNode parts = content.putArray("parts");
            ObjectNode part = parts.addObject();
            part.put("text", m.getTexto());
        }

        byte[] input = objectMapper.writeValueAsBytes(rootNode);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(input, 0, input.length);
        }

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            InputStreamReader errReader = new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8);
            StringBuilder errBody = new StringBuilder();
            try (BufferedReader br = new BufferedReader(errReader)) {
                String line;
                while ((line = br.readLine()) != null) errBody.append(line);
            }
            throw new RuntimeException("Error Google Streaming [" + statusCode + "]: " + errBody);
        }

        StringBuilder textoCompletoAcumulado = new StringBuilder();

        // 3. Lectura continua línea a línea del flujo SSE de Google
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                if (linea.startsWith("data: ")) {
                    String jsonChunk = linea.substring(6).trim();
                    if (!jsonChunk.isEmpty()) {
                        String fragmentoTexto = extraerTextoDeChunk(jsonChunk);
                        if (!fragmentoTexto.isEmpty()) {
                            textoCompletoAcumulado.append(fragmentoTexto);
                            consumidorChunk.accept(fragmentoTexto);
                        }
                    }
                }
            }
        }

        // 4. Guardamos la respuesta entera generada en el historial
        historial.add(new MensajeHistorial("model", textoCompletoAcumulado.toString()));
        podarHistorialSiExcedeLimite(historial);
    }

    private String extraerTextoDeChunk(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText("");
                }
            }
        } catch (Exception ignored) {}
        return "";
    }
}