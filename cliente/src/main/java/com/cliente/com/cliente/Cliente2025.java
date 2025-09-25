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
                        if (lineaServidor.startsWith("_COMANDO_:")) {
                            String[] partes = lineaServidor.split(":");
                            String comando = partes[1];
                            String remitente = partes[2];
                            if ("LISTAR_ARCHIVOS".equalsIgnoreCase(comando)) {
                                File directorio = new File(".");
                                File[] archivos = directorio
                                        .listFiles((dir, nombre) -> nombre.toLowerCase().endsWith(".txt"));
                                escritorServidor.println("_RESPUESTA_LISTAR_ARCHIVOS:" + remitente);
                                if (archivos != null && archivos.length > 0) {
                                    for (File archivo : archivos) {
                                        escritorServidor.println(archivo.getName());
                                    }
                                }
                                escritorServidor.println("_FIN_LISTA_");
                            }
                        } else {
                            System.out.println("Servidor: " + lineaServidor);
                        }
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
