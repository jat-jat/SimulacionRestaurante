package restaurante;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;
import java.util.concurrent.Semaphore;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.JToggleButton;

/**
 * UNIVERSIDAD POLITÉCNICA DE CHIAPAS
 * PROGRAMACIÓN CONCURRENTE - CORTE 2
 * 6 de noviembre del 2017
 * @author Equipo 4
 */
public class Ejecutable {
    public static void main(String[] args) {
        JFrame frame = new JFrame("Simulación de restuarante");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.add(new Ventana());
        frame.pack();
        frame.setVisible(true);
    }
}

class Ventana extends JPanel {
    enum EstadoOrden {
        NOTA, //Acaba de ser pedida, es un papel que tiene escrita la orden.
        LISTO, //La orden ya se cocinó, está bajo una tapa.
        SIENDO_COMIDO //La orden está en la mesa del cliente y ha sido destapada.
    }
    
    //La duración de cada fotograma, en milisegundos.
    static final byte FOTOGRAMA = 10;
    
    //Dibujo de un restaurante.
    BufferedImage img_fondo;
    //La silueta de una mesa.
    BufferedImage img_mesa;
    
    //Generador de números aleatorios.
    Random random;
    
    //Botón que nos permite controlar si se muestran los estados del hilo.
    JToggleButton mostrarEstados;
    
    //Semáforo que controla la fila de entrada.
    Semaphore cola;
    //Almacena a los cliente que estén haciendo cola en un momento determinado.
    ArrayList<Cliente> clientesEnCola;
    
    //Almacena a los clientes que estén dentro del restaurante.
    ArrayList<Cliente> clientesEnPantalla;
    
    //Controlan los lugares o sillas que los clientes ocupan.
    Semaphore semLugares;
    Lugar[] lugares;
    
    //Arreglo que contiene las órdenes que están en el mostrador.
    Orden[] mostrador;
    
    //Los trabajadores.
    Mesero mesero;
    Chef chef;
    
    public Ventana() {
        try {
            img_fondo = ImageIO.read(getClass().getResource("sprites/fondo.png"));
            img_mesa = ImageIO.read(getClass().getResource("sprites/mesa.png"));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
        
        JButton btnAcercaDe = new JButton("Acerca de...");
        btnAcercaDe.addActionListener(new ActionListener(){
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(null, "UP Chiapas\nProgramación concurrente - 7°\n\nPROYECTO DEL CORTE 2\nSimulación de un restaurante con semáforos\n\nJavier Alberto Argüello Tello - 153217\nJosé Julián Molina Ocaña - 153169\nFrancisco Javier de la Cruz Jiménez - 153181\nMauricio Armando Pérez Hernández - 153188\nJaime Francisco Ruiz López - 153189\nMónica Alejandra Peña Robles - 153209");
            }
        });
        add(btnAcercaDe);
        
        mostrarEstados = new JToggleButton("Mostrar estados");
        mostrarEstados.setBackground(Color.yellow);
        add(mostrarEstados);
        
        cola = new Semaphore(10);
        lugares = new Lugar[8];
        semLugares = new Semaphore(lugares.length);
        mesero = new Mesero();
        clientesEnCola = new ArrayList<>();
        clientesEnPantalla = new ArrayList<>();
        mostrador = new Orden[lugares.length];
        random = new Random();
        
        lugares[0] = new Lugar((short)175, (short)220, false);
        lugares[1] = new Lugar((short)175, (short)365, false);
        lugares[2] = new Lugar((short)430, (short)220, false);
        lugares[3] = new Lugar((short)430, (short)365, false);
        lugares[4] = new Lugar((short)(175 + img_mesa.getWidth()), (short)220, true);
        lugares[5] = new Lugar((short)(175 + img_mesa.getWidth()), (short)365, true);
        lugares[6] = new Lugar((short)(430 + img_mesa.getWidth()), (short)220, true);
        lugares[7] = new Lugar((short)(430 + img_mesa.getWidth()), (short)365, true);
        
        for(byte i = 0; i < mostrador.length; i++)
            mostrador[i] = null;
        
        setPreferredSize(new Dimension(640,480));  
        
        //Creamos al chef y a su hilo.
        chef = new Chef();
        Thread hChef = new Thread(chef);
        hChef.setDaemon(true);
        hChef.start();
        
        Thread renderizar = new Thread(() -> {            
            while(true){
                //Renderizamos un fotograma.
                repaint();
                
                try {
                    Thread.sleep(FOTOGRAMA);
                } catch (Exception ex) {
                    break;
                }
            }
        });
        renderizar.setDaemon(true);
        renderizar.start();
        
        Thread generadorDeClientes = new Thread(() -> {            
            while(true){
                try {
                    Thread.sleep(random.nextInt(10 * 1000));
                } catch (Exception ex) {
                    break;
                }
                crearCliente();
            }
        });
        generadorDeClientes.setDaemon(true);
        generadorDeClientes.start();
    }
    
    /**
     * Agrega un cliente al escenario.
     */
    private void crearCliente(){
        try {
            cola.acquire();
        } catch (InterruptedException ex) {
        }
        Cliente nuevoCliente = new Cliente();
        synchronized(clientesEnCola){
            clientesEnCola.add(nuevoCliente);
        }
        synchronized(clientesEnPantalla){
            clientesEnPantalla.add(0, nuevoCliente);
        }
        Thread hiloDelCliente = new Thread(nuevoCliente);
        hiloDelCliente.setDaemon(true);
        hiloDelCliente.start();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        //Obtenemos el objeto que nos permitirá pintar la pantalla.
        Graphics2D lienzo = (Graphics2D) g;
        
        lienzo.drawImage(img_fondo, 0, 0, null);
        for(byte i = 0; i < 4; i++)
            lienzo.drawImage(img_mesa, lugares[i].getX(), lugares[i].getY(), null);
        
        //Mostramos a los clientes.
        synchronized(clientesEnPantalla){
            for(Cliente aux : clientesEnPantalla)
                aux.renderizar(lienzo);
        }
        
        //Mostramos las órdenes del mostrador.
        for(byte i = 0; i < mostrador.length; i++)
            if(mostrador[i] != null)
                lienzo.drawImage(mostrador[i].getGrafico(), 252 + (i * 35), 112, null);
        
        //Mostramos a los trabajadores.
        chef.renderizar(lienzo);
        mesero.renderizar(lienzo);
    }
    
    /**
     * Pinta una imagen de un color, reemplazando sus píxeles negros (con el color RGB: 0, 0, 0).
     * @param img Imagen a pintar.
     * @param c Color que reemplazará al negro.
     */
    public void pintarImagen(BufferedImage img, Color c){
        //Visitamos todos los pixeles de la imagen y cambiamos el color donde sea necesario.
        int colorNegro, colorNuevo, pixelActual;
        colorNegro = Color.BLACK.getRGB();
        colorNuevo = c.getRGB();
        for(short i = 0; i < img.getWidth(); i++)
            for(short j = 0; j < img.getHeight(); j++){
                pixelActual = img.getRGB(i, j);

                if(pixelActual == colorNegro)
                    img.setRGB(i, j, colorNuevo);
            }
    }
    
    /**
     * Invierte una imagen horizontalmente.
     * @param imgAVoltear Imagen que se desea voltear.
     * @return Una copia de la imagen invertida.
     */
    public BufferedImage voltearHorizontoal(BufferedImage imgAVoltear){
        AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
        tx.translate(-imgAVoltear.getWidth(null), 0);
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(imgAVoltear, null);
    }
    
    /**
     * Clase que representa una posición en la pantalla donde se encuentra una silla.
     */
    class Lugar{
        //Posición
        private final short x, y;
        //Si es falso, la silla está a la izquierda de la mesa, si no, a la derecha.
        private final boolean direccion;
        //Indica si el lugar está siendo ocupado por un cliente.
        AtomicBoolean ocupado;

        public Lugar(short x, short y, boolean direccion) {
            this.x = x;
            this.y = y;
            this.direccion = direccion;
            ocupado = new AtomicBoolean(false);
        }

        public short getX() {
            return x;
        }

        public short getY() {
            return y;
        }

        public boolean getDireccion() {
            return direccion;
        }
    }
    
    /**
     * Clase abstracta que representa un objeto móvil que puede ser mostrado en pantalla.
     */
    abstract class Renderizable{
        short posX, posY;
        
        /**
         * Cambia la posición de este objeto.
         * @param x Nueva posición en X.
         * @param y Nueva posición en Y.
         */
        public void moverA(Short x, Short y){
            if(x == null)
                x = posX;
            if(y == null)
                y = posY;
            byte movHorizontal = (byte)(posX < x ? 1 : -1);
            byte movVertical = (byte)(posY < y ? 1 : -1);
            
            while(posX != x || posY != y){
                if(posX != x)
                    posX += movHorizontal;
                if(posY != y)
                    posY += movVertical;

                try {
                    Thread.sleep(FOTOGRAMA);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
        }
        
        /**
         * Imprime este objeto en pantalla.
         * @param lienzo Canvas de swing de un JPanel.
         */
        public abstract void renderizar(Graphics2D lienzo);
    }
    
    class Cliente extends Renderizable implements Runnable{ 
        BufferedImage grafico;
        Color color;
        Orden orden;
        boolean direccion;
        String estado;
        
        public Cliente(){
            color = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
            orden = new Orden(color);
            try {
                grafico = ImageIO.read(getClass().getResource("sprites/cliente_1.png"));
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
            pintarImagen(grafico, color);
            posX = 29;
            posY = -2;
            direccion = true;
            estado = "Nuevo";
        }
        
        @Override
        public void renderizar(Graphics2D lienzo) {
            lienzo.drawImage(grafico, posX, posY, null);
            if(orden.getEstado() == EstadoOrden.SIENDO_COMIDO){
                if(direccion == false)
                    lienzo.drawImage(orden.getGrafico(), posX + 42, posY + 12, null);
                else
                    lienzo.drawImage(orden.getGrafico(), posX - 25, posY + 12, null);
            }
            if(mostrarEstados.isSelected())
                lienzo.drawString(estado, posX, (direccion ? posY + grafico.getHeight() + 10 : posY));
        }

        @Override
        public void run() {
            try {
                direccion = false;
                Thread.sleep(500);
                int posEnCola = -1;
                
                Short nuevaPosicion = null;
                
                do{
                    estado = "Ejecutable - Avanzando en cola";
                    synchronized(clientesEnCola){
                        if(posEnCola != clientesEnCola.indexOf(this)){
                            nuevaPosicion = (short)(384 - (25 * clientesEnCola.indexOf(this)));
                            posEnCola = clientesEnCola.indexOf(this);
                        }
                        else
                            nuevaPosicion = null;
                    }
                    
                    if(nuevaPosicion != null)
                        moverA(null, nuevaPosicion);
                    
                    estado = "Parado - En cola";
                    Thread.sleep(1000);
                } while(posEnCola != 0);
                
                estado = "Parado - Esperando asiento";
                semLugares.acquire();
                
                estado = "Ejecutable - Tomando asiento";
                synchronized(clientesEnCola){
                    clientesEnCola.remove(this);
                }
                cola.release();
                
                Lugar silla = null;
                for(byte i = 0; i < lugares.length; i++)
                    if(lugares[i].ocupado.compareAndSet(false, true)){
                        silla = lugares[i];
                        break;
                    }
                
                if(silla.getDireccion() == false)
                    moverA((short)(silla.getX() + 9), null);
                else               
                    moverA((short)(silla.getX() - 47), null);
                moverA(null, (short)(silla.getY() - 21));
                
                try {
                    grafico = ImageIO.read(getClass().getResource("sprites/cliente_2.png"));
                    pintarImagen(grafico, color);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
                if(silla.getDireccion() == true){
                    direccion = true;
                    grafico = voltearHorizontoal(grafico);
                }
                
                estado = "Parado - Esperando mesero";
                mesero.semaforo.acquire();
                estado = "Ejecutable - Llamando mesero";
                if(direccion == true)
                    mesero.moverA((short)(silla.getX() - grafico.getWidth()), silla.getY());
                else
                    mesero.moverA(silla.getX(), silla.getY());
                estado = "Ejecutable - Mandando orden";
                mesero.ocupar(orden);
                mesero.moverA((short)355, (short)115);
                mesero.liberar();
                
                for(byte i = 0; i < mostrador.length; i++)
                    if(mostrador[i] == null){
                        mostrador[i] = orden;
                        break;
                    }
                mesero.semaforo.release();
                
                estado = "Ejecutable - Checando mi orden";
                boolean yaSeRecibioLaOrden = false;
                while(!yaSeRecibioLaOrden){
                    for(byte i = 0; i < mostrador.length; i++)
                        if(mostrador[i] == orden){
                            if(mostrador[i].getEstado() == EstadoOrden.LISTO){
                                estado = "Parado - Llamando mesero";
                                mesero.semaforo.acquire();
                                estado = "Ejecutable - Solicitando orden";
                                mesero.moverA((short)355, (short)115);
                                mostrador[i] = null;
                                mesero.ocupar(orden);
                                if(direccion == true)
                                    mesero.moverA((short)(silla.getX() - grafico.getWidth()), silla.getY());
                                else
                                    mesero.moverA(silla.getX(), silla.getY());
                                mesero.liberar();
                                mesero.semaforo.release();
                                yaSeRecibioLaOrden = true;
                                break;
                            }
                        }
                    estado = "Parado - Esperando mi orden";
                    Thread.sleep(1000);
                }
                
                orden.siguienteEstado();
                
                try {
                    grafico = ImageIO.read(getClass().getResource("sprites/cliente_3.png"));
                    pintarImagen(grafico, color);
                    posY -= 5;
                    
                    if(silla.getDireccion() == true)
                        grafico = voltearHorizontoal(grafico);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
                
                //Comer
                estado = "Parado - Comiendo";
                Thread.sleep((random.nextInt(5) + 5) * 1000);
                
                estado = "Ejecutable - Saliendo del lugar";
                direccion = false;
                try {
                    grafico = ImageIO.read(getClass().getResource("sprites/cliente_1.png"));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
                pintarImagen(grafico, color);
                
                moverA(null, (short)245);
                moverA((short)640, null);
                
                silla.ocupado.set(false);
                semLugares.release();
                synchronized(clientesEnPantalla){
                    clientesEnPantalla.remove(this);
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }
    
    class Mesero extends Renderizable{        
        private Semaphore semaforo;
        BufferedImage grafico;
        Orden ordenEnMano;
        
        public Mesero(){
            try {
                grafico = ImageIO.read(getClass().getResource("sprites/mesero_1.png"));
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
            posX = 360;
            posY = 190;
            semaforo = new Semaphore(1);
        }
        
        @Override
        public void renderizar(Graphics2D lienzo) {
            lienzo.drawImage(grafico, posX, posY, null);
            if(ordenEnMano != null)
                switch(ordenEnMano.estado){
                    case NOTA:
                        lienzo.drawImage(ordenEnMano.getGrafico(), posX - 8, posY, null);
                        break;
                    case LISTO:
                        lienzo.drawImage(ordenEnMano.getGrafico(), posX - 19, posY + 11, null);
                        break;
                }
        }
        
        public void ocupar(Orden o){
            try {
                try {
                    grafico = ImageIO.read(getClass().getResource("sprites/mesero_2.png"));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
                posX -= 18;
                ordenEnMano = o;
            } catch (Exception e) {}
        }
        
        public void liberar(){
            try {
                grafico = ImageIO.read(getClass().getResource("sprites/mesero_1.png"));
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
            posX += 18;
            ordenEnMano = null;
        }
    }
    
    class Orden{
        private BufferedImage grafico;
        private EstadoOrden estado;
        Color colorDelCliente;
        
        public Orden(Color colorDelCliente){
            try {
                grafico = ImageIO.read(getClass().getResource("sprites/pedido.png"));
                pintarImagen(grafico, colorDelCliente);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
            this.colorDelCliente = colorDelCliente;
            estado = EstadoOrden.NOTA;
        }
        
        public void siguienteEstado(){
            switch(estado){
                case NOTA:
                    try {
                        grafico = ImageIO.read(getClass().getResource("sprites/orden.png"));
                        pintarImagen(grafico, colorDelCliente);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(-1);
                    }
                    estado = EstadoOrden.LISTO;
                    break;
                case LISTO:
                    try {
                        grafico = ImageIO.read(getClass().getResource("sprites/comida.png"));
                        pintarImagen(grafico, colorDelCliente);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(-1);
                    }
                    estado = EstadoOrden.SIENDO_COMIDO;
                    break;
            }
        }

        public BufferedImage getGrafico() {
            return grafico;
        }

        public EstadoOrden getEstado() {
            return estado;
        }
    }
    
    class Chef extends Renderizable implements Runnable{
        BufferedImage grafico, img_esperando, img_cocinando;
        String estado;
        
        public Chef(){
            posX = 340;
            posY = 13;
            try {
                img_esperando = ImageIO.read(getClass().getResource("sprites/chef_libre.png"));
                img_cocinando = ImageIO.read(getClass().getResource("sprites/chef_ocupado.png"));
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
            grafico = img_esperando;
            estado = "Nuevo";
        }
        
        @Override
        public void renderizar(Graphics2D lienzo) {
            lienzo.drawImage(grafico, posX, posY, null);
            if(mostrarEstados.isSelected())
                lienzo.drawString(estado, posX + grafico.getWidth(), posY + (grafico.getHeight() / 2));
        }

        @Override
        public void run() {
            byte i;
            try {
                while(true){
                    for(i = 0; i < mostrador.length; i++){
                        if(mostrador[i] != null)
                            if(mostrador[i].getEstado() == EstadoOrden.NOTA){
                                grafico = img_cocinando;
                                posX += 29;
                                estado = "Parado - Cocinado";
                                Thread.sleep(2000);
                                mostrador[i].siguienteEstado();
                                grafico = img_esperando;
                                posX -= 29;
                                break;
                            }
                    }
                    estado = "Parado - Esperando órdenes";
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
            }
        }
        
    }
}