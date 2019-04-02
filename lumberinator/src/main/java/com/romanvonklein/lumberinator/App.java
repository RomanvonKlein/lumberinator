package com.romanvonklein.lumberinator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class App extends JavaPlugin implements Listener {
    public static final String Version = "0.1";
    private ArrayList<Tree> trees = new ArrayList<Tree>();
    private int maxTasksperTick = 32;
    private ArrayList<ITask> tasks = new ArrayList<ITask>();
    public int leafDecayRange = 3;

    @Override
    public void onEnable() {
        int id = getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                executeTasks();
            }
        }, 0, 1);
        getLogger().info("Activating Lumberinator version '" + Version + "'!");
        this.saveDefaultConfig();

        String jsonString = this.getConfig().getString("data");

        Bukkit.getServer().getPluginManager().registerEvents(this, this);
        JSONParser parser = new JSONParser();
        HashMap<String, JSONObject> obj;
        try {
            obj = (HashMap<String, JSONObject>) parser.parse(jsonString);

            for (Object key : obj.keySet()) {
                LinkedList<BlockCombo> logs = new LinkedList<BlockCombo>();
                LinkedList<BlockCombo> leaves = new LinkedList<BlockCombo>();
                for (HashMap<String, String> blockObj : (List<HashMap<String, String>>) obj.get(key).get("logs")) {
                    logs.add(new BlockCombo(blockObj.get("id"), Byte.parseByte(blockObj.get("data"))));
                }
                for (HashMap<String, String> blockObj : (List<HashMap<String, String>>) obj.get(key).get("leaves")) {
                    leaves.add(new BlockCombo(blockObj.get("id"), Byte.parseByte(blockObj.get("data"))));
                }
                this.trees.add(new Tree((String) key, logs, leaves));
            }
        } catch (ParseException e) {
            getLogger().log(Level.SEVERE, "Could not parse config-file!");
        }

    }

    private void executeTasks() {
        for (int i = 0; i < this.maxTasksperTick; i++) {
            if (this.tasks.size() == 0) {
                break;
            } else {
                this.tasks.remove(0).run();
            }
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("See you again, SpigotMC!");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = (Player) e.getPlayer();
        Block b = (Block) e.getBlock();
        ItemStack tool = p.getInventory().getItemInMainHand();

        // Check wether the block broken is in the list of log-blocks
        if (isAxe(tool) && isTree(b) && !p.isSneaking()) {
            this.tasks.add(new FellTreeTask(getTreeName(b), b.getX(), b.getY(), b.getZ(), b.getWorld(), this,
                    b.getLocation(), tool));
            // get started executing tasks right away for user-feedback
            executeTasks();
        } else {
            p.sendMessage("Not doing stuff. isaxe(tool)=" + isAxe(tool) + ", isTree(b):" + isTree(b)
                    + ", !p.isSneaking():" + !p.isSneaking());
        }
    }

    private boolean isAxe(ItemStack tool) {
        Material toolmat = tool.getType();
        // TODO: have a list of items that are legal for lumberinator, having
        // damagevalue and metadata to check against

        return (toolmat == Material.DIAMOND_AXE || toolmat == Material.GOLD_AXE || toolmat == Material.IRON_AXE
                || toolmat == Material.STONE_AXE || toolmat == Material.WOOD_AXE);
    }

    public boolean canDecay(String treeName, World world, int posx, int posy, int posz) {
        for (int x = posx - 1; x < posx + 2; x++) {
            for (int y = posy - 1; y < posy + 2; y++) {
                for (int z = posz - 1; z < posz + 2; z++) {
                    if (isLog(treeName, world.getBlockAt(x, y, z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isTree(Block block) {
        String treeName = getTreeName(block);
        if (treeName == "none") {
            return false;
        }
        Tree tree = getTree(treeName);
        if (tree == null) {
            return false;
        }
        int posx = block.getX();
        int posy = block.getY() + 1;
        int posz = block.getZ();

        for (int x = posx - 1; x < posx + 2; x++) {
            for (int z = posz - 1; z < posz + 2; z++) {
                Block nextBlock = block.getWorld().getBlockAt(x, posy, z);
                getLogger().info("Checking at: " + x + " " + posy + " " + z);
                if (isLeaf(treeName, nextBlock)) {
                    return true;
                } else if (isLog(treeName, nextBlock)) {
                    return (isTree(nextBlock));
                }
            }
        }
        return false;
    }

    private boolean isBlockFromTreeType(String treeType, Block block) {
        Byte data = block.getData();
        String type = block.getType().toString();
        Tree tree = null;
        for (Tree tree2 : this.trees) {
            if (tree2.name.equals(treeType)) {
                tree = tree2;
            }
        }
        if (tree == null) {
            return false;
        }
        for (BlockCombo log : tree.logs) {
            if (type.equals(log.id) && data == log.data) {
                return true;
            }
        }
        for (BlockCombo leaf : tree.leaves) {
            if (type.equals(leaf.id) && data == leaf.data) {
                return true;
            }
        }
        return false;
    }

    private Tree getTree(String treeType) {
        for (Tree tree : this.trees) {
            if (tree.name.equals(treeType)) {
                return tree;
            }
        }
        return null;
    }

    private boolean isLog(String treeType, Block block) {
        Byte data = block.getData();
        String id = block.getType().toString();
        Tree tree = getTree(treeType);
        for (BlockCombo bc : tree.logs) {
            if (data == bc.data && id.equals(bc.id)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLeaf(String treeType, Block block) {

        Byte data = block.getData();
        String id = block.getType().toString();
        Tree tree = getTree(treeType);
        for (BlockCombo bc : tree.leaves) {
            if (data == bc.data && id.equals(bc.id)) {
                return true;
            }
        }
        return false;
    }

    private String getTreeName(Block block) {
        String type = block.getType().toString();
        Byte data = block.getData();
        for (Tree tree : this.trees) {
            for (BlockCombo log : tree.logs) {
                if (type.equals(log.id) && data == log.data) {
                    return tree.name;
                }
            }
        }
        return "none";
    }

    private class Tree {
        LinkedList<BlockCombo> logs;
        LinkedList<BlockCombo> leaves;
        String name;

        public Tree(String name, LinkedList<BlockCombo> logs, LinkedList<BlockCombo> leaves) {
            this.logs = logs;
            this.leaves = leaves;
            this.name = name;
        }

    }

    private class BlockCombo {
        public BlockCombo(String id, Byte data) {
            this.data = data;
            this.id = id;
        }

        public Byte data;
        public String id;
    }

    private class FellTreeTask implements ITask {
        private String name;
        private String treeName;
        private App app;
        private Location startingLocation;
        private int posx;
        private int posy;
        private int posz;
        private ItemStack tool;
        private World world;

        public FellTreeTask(String treeName, int posx, int posy, int posz, World world, App app,
                Location startingLocation, ItemStack tool) {
            this.name = "FellTreeTask";
            this.treeName = treeName;
            this.posx = posx;
            this.posy = posy;
            this.posz = posz;
            this.world = world;
            this.app = app;
            this.tool = tool;
            this.startingLocation = startingLocation;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public void run() {
            for (int x = -1; x < 2; x++) {
                for (int y = -1; y < 2; y++) {
                    for (int z = -1; z < 2; z++) {
                        Block nextBlock = world.getBlockAt(posx + x, posy + y, posz + z);
                        if ((x != 0 || y != 0 || z != 0) && isBlockFromTreeType(treeName, nextBlock)) {
                            if (isLeaf(this.treeName, nextBlock)) {
                                if (!isAxe(tool)) {
                                    return;
                                }
                                // start leafDecayTask
                                for (ItemStack stack : nextBlock.getDrops(this.tool)) {
                                    nextBlock.getWorld().dropItemNaturally(startingLocation, stack);
                                }
                                nextBlock.setType(Material.AIR);
                                this.app.tasks.add(new DecayLeafTask(treeName, posx + x, posy + y, posz + z, world,
                                        app.leafDecayRange, app, startingLocation, this.tool));
                            } else {
                                if (!isAxe(tool)) {
                                    return;
                                }
                                for (ItemStack stack : nextBlock.getDrops(this.tool)) {
                                    nextBlock.getWorld().dropItemNaturally(startingLocation, stack);
                                }
                                nextBlock.setType(Material.AIR);

                                tool.setDurability((short) (tool.getDurability() + 1));
                                if (tool.getDurability() >= tool.getType().getMaxDurability()) {
                                    tool.setType(Material.AIR);
                                    return;
                                }

                                // start another FellTreeTask
                                this.app.tasks.add(new FellTreeTask(this.treeName, posx + x, posy + y, posz + z,
                                        this.world, this.app, this.startingLocation, this.tool));
                            }
                        }
                    }
                }
            }
        }
    }

    private class DecayLeafTask implements ITask {
        private String name;
        private String treeName;
        private int posx;
        private int posy;
        private ItemStack tool;
        private int posz;
        private int strength = 0;
        private App app;
        private Location startingLocation;
        private World world;

        public DecayLeafTask(String treeName, int posx, int posy, int posz, World world, int strength, App app,
                Location startingLocation, ItemStack tool) {
            this.name = "DecayLeafTask";
            this.treeName = treeName;
            this.app = app;
            this.posx = posx;
            this.posy = posy;
            this.posz = posz;
            this.world = world;
            this.tool = tool;
            this.strength = strength;
            this.startingLocation = startingLocation;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public void run() {
            if (strength <= 0) {
                return;
            }
            // second, add new Tasks to fell other blocks of the tree
            for (int x = -1; x < 2; x++) {
                for (int y = -1; y < 2; y++) {
                    for (int z = -1; z < 2; z++) {
                        Block nextBlock = this.world.getBlockAt(posx + x, posy + y, posz + z);
                        if (isLeaf(this.treeName, nextBlock)
                                && app.canDecay(this.treeName, world, posx + x, posy + y, posz + z)) {
                            for (ItemStack stack : nextBlock.getDrops(this.tool)) {
                                nextBlock.getWorld().dropItemNaturally(startingLocation, stack);
                            }
                            nextBlock.setType(Material.AIR);
                            this.app.tasks.add(new DecayLeafTask(treeName, posx + x, posy + y, posz + z, world,
                                    strength - 1, app, startingLocation, this.tool));
                        }
                    }
                }
            }
        }
    }
}