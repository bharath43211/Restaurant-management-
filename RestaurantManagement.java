import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Restaurant Management System (console based, single file)
 *
 * Features:
 *  - Menu management (add / update price / remove / view)
 *  - Table management (reserve / free / view status)
 *  - Order taking (multiple items per table)
 *  - Billing with GST and discount, saved to bills.txt
 *  - Sales report (today's revenue, bestselling items)
 *  - Menu persistence in menu.txt (CSV)
 *
 * Compile : javac RestaurantManagement.java
 * Run     : java RestaurantManagement
 */
public class RestaurantManagement {

    // ===================== MODEL CLASSES =====================

    static class MenuItem {
        int id;
        String name;
        String category;
        double price;

        MenuItem(int id, String name, String category, double price) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.price = price;
        }

        String toCsv() {
            return id + "," + name + "," + category + "," + price;
        }

        @Override
        public String toString() {
            return String.format("%-4d %-22s %-12s Rs.%8.2f", id, name, category, price);
        }
    }

    static class OrderLine {
        MenuItem item;
        int qty;

        OrderLine(MenuItem item, int qty) {
            this.item = item;
            this.qty = qty;
        }

        double total() {
            return item.price * qty;
        }
    }

    static class Table {
        int number;
        int seats;
        boolean occupied;
        List<OrderLine> order = new ArrayList<>();

        Table(int number, int seats) {
            this.number = number;
            this.seats = seats;
        }
    }

    // ===================== DATA =====================

    static final String MENU_FILE = "menu.txt";
    static final String BILL_FILE = "bills.txt";
    static final double GST_RATE = 0.05;

    static Map<Integer, MenuItem> menu = new LinkedHashMap<>();
    static List<Table> tables = new ArrayList<>();
    static Map<String, Integer> salesCount = new HashMap<>();
    static double totalRevenue = 0;
    static int billCounter = 1;
    static int nextMenuId = 1;

    static Scanner sc = new Scanner(System.in);

    // ===================== MAIN =====================

    public static void main(String[] args) {
        loadMenu();
        for (int i = 1; i <= 8; i++) {
            tables.add(new Table(i, i <= 4 ? 2 : 4));
        }

        System.out.println("=====================================");
        System.out.println("   RESTAURANT MANAGEMENT SYSTEM");
        System.out.println("=====================================");

        while (true) {
            System.out.println("\n----------- MAIN MENU -----------");
            System.out.println("1. Menu Management");
            System.out.println("2. Table Management");
            System.out.println("3. Take / Add Order");
            System.out.println("4. View Table Order");
            System.out.println("5. Generate Bill");
            System.out.println("6. Sales Report");
            System.out.println("0. Exit");
            int choice = readInt("Enter choice: ");

            switch (choice) {
                case 1: menuManagement(); break;
                case 2: tableManagement(); break;
                case 3: takeOrder(); break;
                case 4: viewOrder(); break;
                case 5: generateBill(); break;
                case 6: salesReport(); break;
                case 0:
                    saveMenu();
                    System.out.println("Menu saved. Goodbye!");
                    return;
                default: System.out.println("Invalid choice.");
            }
        }
    }

    // ===================== MENU MANAGEMENT =====================

    static void menuManagement() {
        while (true) {
            System.out.println("\n--- Menu Management ---");
            System.out.println("1. View Menu");
            System.out.println("2. Add Item");
            System.out.println("3. Update Price");
            System.out.println("4. Remove Item");
            System.out.println("0. Back");
            int c = readInt("Enter choice: ");
            switch (c) {
                case 1: viewMenu(); break;
                case 2:
                    System.out.print("Item name: ");
                    String name = sc.nextLine().trim();
                    System.out.print("Category (Starter/Main/Dessert/Drink): ");
                    String cat = sc.nextLine().trim();
                    double price = readDouble("Price: ");
                    if (name.isEmpty() || price <= 0) {
                        System.out.println("Invalid name or price.");
                        break;
                    }
                    menu.put(nextMenuId, new MenuItem(nextMenuId, name, cat, price));
                    System.out.println("Added with ID " + nextMenuId);
                    nextMenuId++;
                    saveMenu();
                    break;
                case 3:
                    int id = readInt("Item ID: ");
                    MenuItem m = menu.get(id);
                    if (m == null) { System.out.println("Item not found."); break; }
                    double np = readDouble("New price: ");
                    if (np <= 0) { System.out.println("Invalid price."); break; }
                    m.price = np;
                    saveMenu();
                    System.out.println("Price updated.");
                    break;
                case 4:
                    int rid = readInt("Item ID to remove: ");
                    if (menu.remove(rid) != null) {
                        saveMenu();
                        System.out.println("Item removed.");
                    } else {
                        System.out.println("Item not found.");
                    }
                    break;
                case 0: return;
                default: System.out.println("Invalid choice.");
            }
        }
    }

    static void viewMenu() {
        System.out.println("\nID   Name                   Category     Price");
        System.out.println("---------------------------------------------------");
        if (menu.isEmpty()) System.out.println("(menu is empty)");
        for (MenuItem m : menu.values()) System.out.println(m);
    }

    // ===================== TABLE MANAGEMENT =====================

    static void tableManagement() {
        while (true) {
            System.out.println("\n--- Table Management ---");
            System.out.println("1. View Table Status");
            System.out.println("2. Seat Guests (occupy table)");
            System.out.println("0. Back");
            int c = readInt("Enter choice: ");
            switch (c) {
                case 1:
                    System.out.println("\nTable  Seats  Status");
                    for (Table t : tables) {
                        System.out.printf("%-6d %-6d %s%n", t.number, t.seats,
                                t.occupied ? "OCCUPIED" : "Free");
                    }
                    break;
                case 2:
                    Table t = getTable();
                    if (t == null) break;
                    if (t.occupied) System.out.println("Table already occupied.");
                    else { t.occupied = true; System.out.println("Table " + t.number + " is now occupied."); }
                    break;
                case 0: return;
                default: System.out.println("Invalid choice.");
            }
        }
    }

    static Table getTable() {
        int n = readInt("Table number (1-" + tables.size() + "): ");
        if (n < 1 || n > tables.size()) {
            System.out.println("Invalid table.");
            return null;
        }
        return tables.get(n - 1);
    }

    // ===================== ORDERS =====================

    static void takeOrder() {
        Table t = getTable();
        if (t == null) return;
        t.occupied = true;

        viewMenu();
        while (true) {
            int id = readInt("Item ID (0 to finish): ");
            if (id == 0) break;
            MenuItem m = menu.get(id);
            if (m == null) { System.out.println("Item not found."); continue; }
            int qty = readInt("Quantity: ");
            if (qty <= 0) { System.out.println("Invalid quantity."); continue; }

            boolean merged = false;
            for (OrderLine ol : t.order) {
                if (ol.item.id == id) { ol.qty += qty; merged = true; break; }
            }
            if (!merged) t.order.add(new OrderLine(m, qty));
            System.out.println("Added " + qty + " x " + m.name);
        }
    }

    static void viewOrder() {
        Table t = getTable();
        if (t == null) return;
        if (t.order.isEmpty()) { System.out.println("No order for this table."); return; }
        printOrder(t);
    }

    static void printOrder(Table t) {
        System.out.println("\nTable " + t.number + " - Order");
        System.out.printf("%-22s %5s %10s%n", "Item", "Qty", "Amount");
        double sub = 0;
        for (OrderLine ol : t.order) {
            System.out.printf("%-22s %5d %10.2f%n", ol.item.name, ol.qty, ol.total());
            sub += ol.total();
        }
        System.out.printf("%-22s %5s %10.2f%n", "Subtotal", "", sub);
    }

    // ===================== BILLING =====================

    static void generateBill() {
        Table t = getTable();
        if (t == null) return;
        if (t.order.isEmpty()) { System.out.println("Nothing to bill for this table."); return; }

        double subtotal = 0;
        for (OrderLine ol : t.order) subtotal += ol.total();

        double discountPct = readDouble("Discount % (0 for none): ");
        if (discountPct < 0 || discountPct > 100) discountPct = 0;

        double discount = subtotal * discountPct / 100.0;
        double taxable = subtotal - discount;
        double gst = taxable * GST_RATE;
        double grand = taxable + gst;

        StringBuilder sb = new StringBuilder();
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
        sb.append("========================================\n");
        sb.append("            RESTAURANT BILL\n");
        sb.append("========================================\n");
        sb.append(String.format("Bill No: %04d      Table: %d%n", billCounter, t.number));
        sb.append("Date: ").append(time).append("\n");
        sb.append("----------------------------------------\n");
        sb.append(String.format("%-20s %4s %10s%n", "Item", "Qty", "Amount"));
        for (OrderLine ol : t.order) {
            sb.append(String.format("%-20s %4d %10.2f%n", ol.item.name, ol.qty, ol.total()));
            salesCount.merge(ol.item.name, ol.qty, Integer::sum);
        }
        sb.append("----------------------------------------\n");
        sb.append(String.format("%-25s %10.2f%n", "Subtotal", subtotal));
        sb.append(String.format("%-25s %10.2f%n", "Discount (" + discountPct + "%)", -discount));
        sb.append(String.format("%-25s %10.2f%n", "GST (5%)", gst));
        sb.append(String.format("%-25s %10.2f%n", "GRAND TOTAL", grand));
        sb.append("========================================\n");
        sb.append("        Thank you! Visit again.\n\n");

        System.out.println("\n" + sb);

        try (FileWriter fw = new FileWriter(BILL_FILE, true)) {
            fw.write(sb.toString());
        } catch (IOException e) {
            System.out.println("Could not save bill: " + e.getMessage());
        }

        totalRevenue += grand;
        billCounter++;
        t.order.clear();
        t.occupied = false;
    }

    // ===================== REPORT =====================

    static void salesReport() {
        System.out.println("\n--- Sales Report (this session) ---");
        System.out.printf("Bills generated : %d%n", billCounter - 1);
        System.out.printf("Total revenue   : Rs.%.2f%n", totalRevenue);
        if (salesCount.isEmpty()) { System.out.println("No sales yet."); return; }

        System.out.println("\nBestselling items:");
        salesCount.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(5)
                .forEach(e -> System.out.printf("  %-22s %d sold%n", e.getKey(), e.getValue()));
    }

    // ===================== FILE HANDLING =====================

    static void loadMenu() {
        File f = new File(MENU_FILE);
        if (!f.exists()) {
            addDefaultMenu();
            saveMenu();
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length < 4) continue;
                int id = Integer.parseInt(p[0].trim());
                menu.put(id, new MenuItem(id, p[1].trim(), p[2].trim(), Double.parseDouble(p[3].trim())));
                nextMenuId = Math.max(nextMenuId, id + 1);
            }
        } catch (Exception e) {
            System.out.println("Error reading menu, loading defaults.");
            menu.clear();
            addDefaultMenu();
        }
    }

    static void saveMenu() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(MENU_FILE))) {
            for (MenuItem m : menu.values()) pw.println(m.toCsv());
        } catch (IOException e) {
            System.out.println("Could not save menu: " + e.getMessage());
        }
    }

    static void addDefaultMenu() {
        String[][] d = {
            {"Paneer Tikka", "Starter", "180"},
            {"Veg Spring Roll", "Starter", "120"},
            {"Butter Naan", "Main", "40"},
            {"Paneer Butter Masala", "Main", "220"},
            {"Veg Biryani", "Main", "190"},
            {"Gulab Jamun", "Dessert", "70"},
            {"Ice Cream", "Dessert", "80"},
            {"Masala Tea", "Drink", "30"},
            {"Cold Coffee", "Drink", "90"}
        };
        nextMenuId = 1;
        for (String[] r : d) {
            menu.put(nextMenuId, new MenuItem(nextMenuId, r[0], r[1], Double.parseDouble(r[2])));
            nextMenuId++;
        }
    }

    // ===================== INPUT HELPERS =====================

    static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    static double readDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Double.parseDouble(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }
}
