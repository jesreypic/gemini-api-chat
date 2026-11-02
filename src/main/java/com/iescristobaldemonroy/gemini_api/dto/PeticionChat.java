package com.iescristobaldemonroy.gemini_api.dto;

public class PeticionChat {
    private String mensaje;

    public PeticionChat() {
    }

    public PeticionChat(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}