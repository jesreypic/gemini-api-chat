package com.iescristobaldemonroy.gemini_api.dto;

public class RespuestaChat {
    private String respuesta;

    public RespuestaChat() {
    }

    public RespuestaChat(String respuesta) {
        this.respuesta = respuesta;
    }

    public String getRespuesta() {
        return respuesta;
    }

    public void setRespuesta(String respuesta) {
        this.respuesta = respuesta;
    }
}