package com.cliente;

import java.io.*;
import java.net.Socket;

public class Cliente2025 {

    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 8080);
             BufferedReader lectorServidor = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter escritorServidor = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in))) {

            Thread lectorThread = new Thread(() -> {
                String lineaServidor;
                try {
                    while ((lineaServidor = lectorServidor.readLine()) != null) {
                        System.out.println("Servidor: " + lineaServidor);
                    }
                } catch (IOException e) {
                    System.err.println("Conexion perdida con el servidor.");
                }
            });
            lectorThread.start();

            String comando;
            while ((comando = teclado.readLine()) != null) {
                escritorServidor.println(comando);
            }

        } catch (IOException e) {
            System.err.println("Error en la conexión o I/O: " + e.getMessage());
        }
    }
}