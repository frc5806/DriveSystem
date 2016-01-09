import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * A GUI menu for the user. Entry point into the program. Allows the user to
 * stop the program.
 */
public class Gooey extends JFrame {
    // Main panel.
    private JPanel panel;
    
    int testVariable;
    
    // Start and stop buttons.
    private JButton startButton, stopButton;

    /*
     * GUI constructor.
     *
     * Makes and attaches methods to buttons, and starts a listener thread.
     */
    public Gooey() {
        super("Noble Driver Station");
        setSize(300, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new FlowLayout());

        startButton = new JButton("START");
        stopButton = new JButton("STOP");

        startButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    System.out.println("action");
                    start();
                }
            });

        stopButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    stop();
                }
            });

        addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    stop();
                }
            });

        add(startButton);
        add(stopButton);
        setVisible( true);
    }

    /*
     * Listener thread soft-start function.
     *
     * Sets motors values away from zero (undoes the stop() function).
     */
    public void start() {
        
    }

    /*
     * Listener thread soft-stop function.
     *
     * Sets sent motor values to zero.
     */
    public void stop() {
        
    }

    public static void main(String[] args) {
        System.out.println("\u000c");
        Gooey mainWindow = new Gooey();
    }
}