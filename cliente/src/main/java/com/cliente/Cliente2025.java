package com.cliente;

import java.io.*;
import java.net.Socket;

public class Cliente2025 {

    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 8080);
             BufferedReader lectorServidor = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter escritorServidor = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in))) {

            String lineaServidor;
            while ((lineaServidor = lectorServidor.readLine()) != null) {
                System.out.println("Servidor: " + lineaServidor);

                if (lineaServidor.toLowerCase().contains("escribe") ||
                    lineaServidor.toLowerCase().contains("introduce") ||
                    lineaServidor.toLowerCase().contains("elige") ||
                    lineaServidor.toLowerCase().contains("adivina el numero") ||
                    lineaServidor.toLowerCase().contains("mayor") ||
                    lineaServidor.toLowerCase().contains("menor")) {
                        
                    System.out.print("Tu respuesta: ");
                    String respuesta = teclado.readLine();
                    escritorServidor.println(respuesta);

                } else if (lineaServidor.toLowerCase().contains("correcto!")) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error en la conexión o I/O: " + e.getMessage());
        }
    }
}