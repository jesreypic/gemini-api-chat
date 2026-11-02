# Gemini API Chat Assistant (Spring Boot)

Aplicación web conversacional completa construida con **Spring Boot**, **Java** y la API oficial de **Google Gemini** (`gemini-3.5-flash-lite`). Diseñada con un enfoque de depuración rigurosa, tolerancia a fallos, control de concurrencia y prevención de fugas de memoria.

---

## Características Principales

* **Integración Oficial con Gemini API:** Comunicación directa vía HTTPS y autenticación por API Key mediante cabeceras HTTP seguras.
* **Aislamiento Multiusuario por Sesión:** Gestión de conversaciones concurrentes mediante `ConcurrentHashMap` vinculado a la `HttpSession` de cada navegador (identificación mediante cookie `JSESSIONID`).
* **Prevención de Fugas de Memoria:** Ventana deslizante que retiene un máximo de 10 mensajes por sesión, controlando el consumo de memoria y la cuota de tokens.
* **Serialización Segura con Jackson:** Parseo robusto de árboles JSON (`JsonNode`) y manejo de respuestas especiales del modelo (`finishReason`, `SAFETY`, bloqueos éticos).
* **Validación de Carga Útil:** Rechazo preventivo de mensajes vacíos (HTTP 400) y de cargas superiores a 4.000 caracteres (HTTP 413 `Payload Too Large`).
* **Resiliencia de Red:** Tiempos de espera configurados a 20 s de conexión y 30 s de lectura con fallback automático simulado ante caídas de servicio.
* **Frontend Reactivo e Intuitivo:** Interfaz HTML5/CSS3/JavaScript con bloqueo de controles para evitar envíos duplicados, indicador animado de procesamiento y renderizado de Markdown/código con `marked.js`.

---

## Tecnologías Utilizadas

* **Lenguaje:** Java 17+ (o Java 21)
* **Framework:** Spring Boot 3.x / 4.x
* **Gestor de dependencias y construcción:** Apache Maven
* **Librería JSON:** Jackson (`com.fasterxml.jackson.core:jackson-databind`)
* **Frontend:** Vanilla JavaScript, HTML5, CSS3, Marked.js (CDN)

---

## Estructura del Proyecto

```text
gemini-api/
├── src/
│   ├── main/
│   │   ├── java/com/iescristobaldemonroy/gemini_api/
│   │   │   ├── controller/
│   │   │   │   └── ChatController.java       # Endpoints REST (/api/chat, /api/chat/limpiar)
│   │   │   ├── dto/
│   │   │   │   ├── PeticionChat.java         # DTO de entrada (prompt)
│   │   │   │   └── RespuestaChat.java        # DTO de salida (respuesta)
│   │   │   ├── service/
│   │   │   │   └── GeminiService.java        # Lógica de conexión, Jackson y sesiones
│   │   │   └── GeminiApiApplication.java     # Clase principal de Spring Boot
│   │   └── resources/
│   │       ├── static/
│   │       │   └── index.html                # Interfaz web y cliente JS
│   │       └── application.properties        # Configuración de puerto y API Key
│   └── test/
├── pom.xml                                   # Configuración de dependencias Maven
└── README.md