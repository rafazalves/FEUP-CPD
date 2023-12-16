import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This program demonstrates a simple TCP/IP socket server.
 * It uses a thread pool to handle multiple clients connected at the same time.
 * Each client needs to write their username upon connection to save it on the server.
 * Every message sent by the client will be displayed on the server terminal.
 * The server will save the client's username, rank, position x, and position y.
 * If the client disconnects and reconnects, the server will still have their information.
 *
 * To stop the server, type "exit" in the server terminal.
 *
 */
public class TimeServer {
    private static HashMap<String, ClientInfo> clients = new HashMap<>();
    private static final String USERS_FILE = "../doc/users.txt";
    private static Lock clientsLock = new ReentrantLock(); // Lock for clients HashMap
    private static List<ClientInfo> searchSimple = new ArrayList<>();
    private static List<ClientInfo> searchRanked = new ArrayList<>();
    private static List<ClientInfo> playSimple = new ArrayList<>();
    private static List<ClientInfo> playRanked = new ArrayList<>();
    public static boolean gameSimple_playing = false;
    public static boolean gameRanked_playing = false;
    public static Game simple = null;
    public static Game ranked = null;

    public static void main(String[] args) {
        if (args.length < 1) return;

        int port = Integer.parseInt(args[0]);

        AtomicBoolean isRunning = new AtomicBoolean(true);
        Thread consoleThread = null;

        loadUsersFromFile();

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            System.out.println("Server is listening on port " + port);

            // Listen for console input to gracefully stop the server
            consoleThread = new Thread(() -> {
                BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
                while (isRunning.get()) {
                    try {
                        String input = consoleReader.readLine();
                        if (input.equalsIgnoreCase("exit")) {
                            System.out.println("Shutting down server...");
                            isRunning.set(false);

                            // Notify all connected clients before shutting down
                            clientsLock.lock(); // Acquire lock before modifying clients HashMap
                            try {
                                for (ClientInfo client : clients.values()) {
                                    try {
                                        PrintWriter clientWriter = new PrintWriter(client.getSocket().getOutputStream(), true);
                                        clientWriter.println("Server is shutting down. Goodbye!");
                                        client.getSocket().close();
                                    } catch (IOException ex) {
                                        System.out.println("Error notifying client " + client.getUsername());
                                        ex.printStackTrace();
                                    }
                                }
                            } finally {
                                clientsLock.unlock(); // Release lock after modifying clients HashMap
                            }
                            System.exit(0); // Shut down the program
                        }

                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });
            consoleThread.start();

            while (isRunning.get()) {
                Socket socket = serverSocket.accept();

                Thread clientThread = new Thread(new ClientHandler(socket));
                clientThread.start();
            }

        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            if (consoleThread != null) consoleThread.interrupt();
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            String username = null;
            String password = null;
            try {
                InputStream input = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                OutputStream output = socket.getOutputStream();
                PrintWriter writer = new PrintWriter(output, true);

                // Get client username
                username = reader.readLine();
                // Get client password
                password = reader.readLine();

            clientsLock.lock(); // Acquire lock before modifying clients HashMap
            try {
                // Check if client is already connected
                if(username!=null && password!=null){
                    if (!clients.containsKey(username)) {
                        // Add client to HashMap
                        ClientInfo clientInfo = new ClientInfo(username, password, 0, 0, 0, socket, Thread.currentThread());
                        clients.put(username, clientInfo);
                        System.out.println("New client connected: " + username + " Pass = " + password);
                        saveClientsToFile();
                        clients.get(username).updateConnection();
                        
                        writer.println("Welcome to Summing " + username);
                        writer.println("Write 'S' to inicialize a normal game");
                        writer.println("Write 'R' to inicialize a ranked game");
                        writer.println("Write 'L' to see your rank");
                        writer.println("Write 'I' to learn about the game");
                        writer.println("Write 'Q' to quit the game");
                    }else if(clients.containsKey(username) && !clients.get(username).getPassword().equals(password)){
                        writer.println("Wrong username or password. Reconnect and try again!");
                        username=null;
                    }else if(clients.containsKey(username) && !clients.get(username).getConnection()){
                        System.out.println("Client " + username + " has reconnected.");
                        clients.get(username).setSocket(socket);
                        clients.get(username).setThread(Thread.currentThread());
                        clients.get(username).updateConnection();
                        
                        if(clients.get(username).getStatus()){
                            writer.println("You were in game when you disconnected");
                            if(clients.get(username).getTurn()){
                                writer.println("Its your turn to play!");
                            }
                        }else if(!clients.get(username).getStatus() && searchSimple.contains(clients.get(username))){
                            writer.println("You were searching for a simple game when you disconnected");
                        }else if(!clients.get(username).getStatus() && searchRanked.contains(clients.get(username))){
                            writer.println("You were searching for a ranked game when you disconnected");
                        }else{
                            writer.println("Welcome back to Summing " + username);
                            writer.println("Write 'S' to inicialize a normal game");
                            writer.println("Write 'R' to inicialize a ranked game");
                            writer.println("Write 'L' to see your rank");
                            writer.println("Write 'I' to learn about the game");
                            writer.println("Write 'Q' to quit the game");
                        }
                    }else{
                        writer.println("This player is already connected to the server. Reconnect and try again later!");
                        username=null;
                    }
                }
            } finally {
                clientsLock.unlock(); // Release lock after modifying clients HashMap
            }

            // Listen for messages from client
            String message;
            while (true) {
                if(searchSimple.size() >= 4 && !gameSimple_playing){
                    for (ClientInfo p : searchSimple.subList(0, 4)) {
                        playSimple.add(p);
                    }
                    simple = new Game(playSimple, false);
                    for (ClientInfo p : playSimple) {
                        p.setGame(simple);
                    }
                    gameSimple_playing = true;
                    simple.start();
                }

                if(searchRanked.size() >= 4 && !gameRanked_playing){
                    for (ClientInfo p : searchRanked.subList(0, 4)) {
                        playRanked.add(p);
                    }
                    ranked = new Game(playRanked, true);
                    for (ClientInfo p : playRanked) {
                        p.setGame(ranked);
                    }
                    gameRanked_playing = true;
                    ranked.start();
                }        

                if((message = reader.readLine()) == null) break;

                //System.out.println("Message from " + username + ": " + message);

                if(!clients.get(username).getStatus() && !clients.get(username).getSearching()){
                    if(message.equalsIgnoreCase("S")){
                        searchSimple.add(clients.get(username));
                        clients.get(username).updateSearching();
                        if(gameSimple_playing){
                            writer.println("A normal game is taking place. The queue could take some time");
                        }
                        writer.println("Searching for normal game...");
                        
                    }else if(message.equalsIgnoreCase("R")){
                        searchRanked.add(clients.get(username));
                        clients.get(username).updateSearching();
                        if(gameRanked_playing){
                            writer.println("A ranked game is taking place. The queue could take some time");
                        }
                        writer.println("Searching for ranked game...");
                        
                    }else if(message.equalsIgnoreCase("L")){
                        writer.println("Your rank is " + clients.get(username).getRank() + ". You have " + clients.get(username).getWins() + " wins and " + clients.get(username).getLoses() + " losses.");
                    }else if(message.equalsIgnoreCase("I")){
                        writer.println("The game consists of adding up numbers entered by players, in order to guess a random number.\nIt has two distinct modes, Simple and Rank and it was designed to be played in two teams of two players each.\nThe game only ends when one of the teams guesses the number.");
                    }else if(message.equalsIgnoreCase("Q")){
                        writer.println("Good bye!");
                        break;
                    }else{
                        writer.println("Thats not an option");
                    }
                }else if(clients.get(username).getSearching()){
                    writer.println("You are searching for a game. Be patient!");
                }else if(clients.get(username).getTurn()){
                    try {
                        int num = Integer.parseInt(message);
                        clients.get(username).setNumber(num);
                        clients.get(username).updateTurn();
                        if(simple.players.contains(clients.get(username))){
                            simple.stuff(clients.get(username), num);
                            if (simple.checkWinner()) {
                                gameSimple_playing = false;
                                playSimple.clear();
                                searchSimple.removeAll(searchSimple.subList(0, 4));
                                simple = null;
                                saveClientsToFile();
                            }else{
                                simple.changeTurn();
                                simple.players.get(simple.playerTurn).updateTurn();
                            }

                        }else{
                            ranked.stuff(clients.get(username), num);
                            if (ranked.checkWinner()) {
                                gameRanked_playing = false;
                                searchRanked.removeAll(searchRanked.subList(0, 4));
                                ranked = null;
                                saveClientsToFile();
                            }else{
                                ranked.changeTurn();
                                ranked.players.get(ranked.playerTurn).updateTurn();
                            }
                        
                        }
                    } catch (NumberFormatException e) {
                        writer.println("Write a number.");
                    }
                }else if(clients.get(username).getStatus()){
                    writer.println("Its not your turn");
                }else{
                    writer.println("ERROR");
                }

                // TODO: Process client message here
            }

            if(username!=null){
                clients.get(username).updateConnection();
                System.out.println("Client disconnected: " + username);
            }else{
                System.out.println("Error connecting a client to the server");
            }

        } catch (IOException ex) {
            if(username==null){
                System.out.println("Error connecting a client to the server");
            }else{
                clients.get(username).updateConnection();
                System.out.println("Client disconnected: " + username);
            }
        }
    }
}
    // Save the client information to the file
    private static void saveClientsToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(USERS_FILE))) {
            for (ClientInfo clientInfo : clients.values()) {
                writer.write(clientInfo.getUsername() + "," +
                             clientInfo.getPassword() + "," +
                             clientInfo.getRank() + "," +
                             clientInfo.getWins() + "," +
                             clientInfo.getLoses());
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Load existing users from file into the HashMap
    private static void loadUsersFromFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader(USERS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    String username = parts[0].trim();
                    String password = parts[1].trim();
                    int rank = Integer.parseInt(parts[2].trim());
                    int wins = Integer.parseInt(parts[3].trim());
                    int loses = Integer.parseInt(parts[4].trim());
                    // Add the user to the clients HashMap
                    ClientInfo clientInfo = new ClientInfo(username, password, rank, wins, loses, null, null);
                    clients.put(username, clientInfo);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}