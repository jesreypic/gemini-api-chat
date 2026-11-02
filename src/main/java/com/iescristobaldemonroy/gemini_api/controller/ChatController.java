package com.iescristobaldemonroy.gemini_api.controller;

import com.iescristobaldemonroy.gemini_api.dto.PeticionChat;
import com.iescristobaldemonroy.gemini_api.dto.RespuestaChat;
import com.iescristobaldemonroy.gemini_api.service.GeminiService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final int MAX_CARACTERES_PROMPT = 4000;
    private final GeminiService geminiService;

    public ChatController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> enviarMensaje(@RequestBody PeticionChat peticion, HttpSession session) {
        try {
            // 1. Validación de nulidad o contenido en blanco
            if (peticion == null || peticion.getMensaje() == null || peticion.getMensaje().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new RespuestaChat("El mensaje no puede estar vacío."));
            }

            String mensajeLimpio = peticion.getMensaje().trim();

            // 2. Validación de longitud máxima
            if (mensajeLimpio.length() > MAX_CARACTERES_PROMPT) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE) // HTTP 413
                        .body(new RespuestaChat("El mensaje excede el límite permitido de " + MAX_CARACTERES_PROMPT + " caracteres."));
            }

            // 3. Envío al servicio con sesión aislada
            String respuesta = geminiService.enviarMensaje(session.getId(), mensajeLimpio);
            return ResponseEntity.ok(new RespuestaChat(respuesta));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RespuestaChat("Error interno del servidor: " + e.getMessage()));
        }
    }

    @PostMapping("/limpiar")
    public ResponseEntity<Void> limpiarHistorial(HttpSession session) {
        geminiService.limpiarHistorial(session.getId());
        return ResponseEntity.ok().build();
    }
}