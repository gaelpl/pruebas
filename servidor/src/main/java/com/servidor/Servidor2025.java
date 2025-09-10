package com.servidor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Servidor2025 {

    public static void main(String[] args) {
        ServerSocket socketEspecial = null;
        Socket cliente = null;
        PrintWriter escritor = null;
        BufferedReader lectorSocket = null;
        BufferedReader teclado = null;

        try {
            socketEspecial = new ServerSocket(8080);
            System.out.println("Servidor esperando conexiones en el puerto 8080...");
            cliente = socketEspecial.accept();
            System.out.println("Cliente conectado desde: " + cliente.getInetAddress().getHostAddress());

            escritor = new PrintWriter(cliente.getOutputStream(), true);
            lectorSocket = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
            teclado = new BufferedReader(new InputStreamReader(System.in));

            String entradaCliente;
            String mensajeServidor;

            while ((entradaCliente = lectorSocket.readLine()) != null) {
                System.out.println("Cliente: " + entradaCliente.toUpperCase());

                if (entradaCliente.equalsIgnoreCase("FIN")) {
                    System.out.println("El cliente ha terminado la conversación.");
                    break;
                }

                System.out.print("Servidor: ");
                mensajeServidor = teclado.readLine();
                escritor.println(mensajeServidor);

                if (mensajeServidor.equalsIgnoreCase("FIN")) {
                    System.out.println("El servidor ha terminado la conversación.");
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("Error de I/O en el servidor: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (teclado != null) teclado.close();
                if (lectorSocket != null) lectorSocket.close();
                if (escritor != null) escritor.close();
                if (cliente != null) cliente.close();
                if (socketEspecial != null) socketEspecial.close();
            } catch (IOException e) {
                System.err.println("Error al cerrar los recursos: " + e.getMessage());
            }
        }
    }
}