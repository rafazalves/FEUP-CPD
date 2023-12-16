import java.net.*;
import java.io.*;
import java.util.*;
 
/**
 * This program demonstrates a simple TCP/IP socket client.
 */
public class TimeClient {
 
    public static void main(String[] args) {
        if (args.length < 2) return;
 
        String hostname = args[0];
        int port = Integer.parseInt(args[1]);

        Scanner scanner = new Scanner(System.in);
 
        try (Socket socket = new Socket(hostname, port)) {
 
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);
 
            System.out.print("Enter your username: ");
            String username = scanner.nextLine();
            writer.println(username);

            Console console = System.console();
            if (console == null) {
                System.err.println("Cannot mask password input in your environment.");
                System.exit(1);
            }

            char[] passwordChars = console.readPassword("Enter your password: ");
            String password = new String(passwordChars);
            Arrays.fill(passwordChars, ' '); // Preenche o array de senha com espaços em branco

            writer.println(password);

            Thread serverListener = new Thread(() -> {
                try {
                    InputStream input = socket.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(input));
 
                    String serverResponse;
                    while ((serverResponse = reader.readLine()) != null) {
                        System.out.println(serverResponse);
                        if(serverResponse.equals("Server is shutting down. Goodbye!") || serverResponse.equals("Good bye!") || serverResponse.equals("Wrong username or password. Reconnect and try again!") || serverResponse.equals("This player is already connected to the server. Reconnect and try again later!")){
                            System.exit(0); // Shut down the program
                        }                        
                    }
                } catch (IOException ex) {
                    System.out.println("Error reading from server");
                    System.exit(0); // Shut down the program
                }
            });

            serverListener.start();

            while (true) {
                String userInput = scanner.nextLine();
                writer.println(userInput);
            }
 
        } catch (UnknownHostException ex) {
            System.out.println("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("I/O error: " + ex.getMessage());
        }
        scanner.close();
    }
}
