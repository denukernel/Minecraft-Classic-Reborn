package net.classicremastered.minecraft.path;

import java.util.*;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;

/** Bounded A* for grounded mobs. Nodes contain feet height rather than eye/center height. */
public final class Pathfinder {
    private Pathfinder() {}
    public static final class Node {
        public final int x,y,z;
        public final float feetY;
        public final Node parent;
        public Node(int x,int y,int z,Node parent){this(x,(float)y,z,parent);}
        public Node(int x,float feetY,int z,Node parent){this.x=x;this.y=(int)Math.floor(feetY);this.feetY=feetY;this.z=z;this.parent=parent;}
        @Override public boolean equals(Object other){return other instanceof Node n && n.x==x && n.feetY==feetY && n.z==z;}
        @Override public int hashCode(){return (x*73856093)^((int)(feetY*2)*19349663)^(z*83492791);}
    }
    private record Cell(int x,int y,int z) {}
    private record Entry(Node node,float cost,float score,long order) {}
    private static final int[][] DIRECTIONS={{1,0},{-1,0},{0,1},{0,-1}};
    public static float offset(float width){return ((int)Math.ceil(width)&1)==0?0f:0.5f;}
    public static int cell(float coordinate,float width){return Math.round(coordinate-offset(width));}
    public static boolean canWalkStraight(Level level,Mob mob,float x,float feetY,float z) {
        return new GroundClearance(level,mob.bbWidth,mob.bbHeight).walkableLine(mob.x,mob.bb.y0,mob.z,x,feetY,z);
    }
    public static boolean hasStandingRoom(Level level,Mob mob,float x,float feetY,float z) {
        GroundClearance ground=new GroundClearance(level,mob.bbWidth,mob.bbHeight);
        float space=0.4f+mob.bbWidth*0.5f;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
            if(!ground.supportedAt(x+dx*space,feetY,z+dz*space))return false;
        return true;
    }

    /** Compatibility overload: maxNodes is an expansion budget, not a distance. */
    public static List<Node> findPath(Level level,int sx,int sy,int sz,int tx,int ty,int tz,int maxNodes) {
        return search(new GroundClearance(level,0.6f,1.8f),sx,sy,sz,tx,ty,tz,maxNodes,24);
    }
    public static List<Node> findPath(Level level,Mob mob,int tx,int ty,int tz,int maxNodes) {
        return findPath(level,mob,tx,(float)ty,tz,maxNodes);
    }
    public static List<Node> findPath(Level level,Mob mob,int tx,float ty,int tz,int maxNodes) {
        GroundClearance ground=new GroundClearance(level,mob.bbWidth,mob.bbHeight);
        int sx=cell(mob.x,mob.bbWidth),sz=cell(mob.z,mob.bbWidth);
        float sy=Math.round(mob.bb.y0*2f)/2f;
        if(Math.abs(mob.bb.y0-sy)<0.12f && ground.standable(sx,sy,sz)
                && ground.bodyLineClear(mob.x,Math.max(mob.bb.y0,sy),mob.z,sx+ground.offset,sz+ground.offset)) {
            return search(ground,sx,sy,sz,tx,ty,tz,maxNodes,24);
        }
        Node start=null;float best=Float.POSITIVE_INFINITY;
        // A mob can land with its leading edge on a tread before its center crosses
        // the grid boundary. Anchor a replan on reachable ground at that height,
        // rather than sending it backward to the lower cell under its center.
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
            for(float delta:new float[]{0,-0.5f,0.5f,-1,1,-1.5f,1.5f,-2,2}) {
                int x=sx+dx,z=sz+dz;float y=sy+delta;
                if(!ground.standable(x,y,z))continue;
                float px=x+ground.offset-mob.x,pz=z+ground.offset-mob.z,py=y-mob.bb.y0;
                float score=px*px+pz*pz+py*py*4;
                if(score>=best || !ground.bodyLineClear(mob.x,Math.max(mob.bb.y0,y),mob.z,x+ground.offset,z+ground.offset))continue;
                start=new Node(x,y,z,null);best=score;
            }
        return start==null?null:search(ground,start.x,start.feetY,start.z,tx,ty,tz,maxNodes,24);
    }
    private static float heuristic(int x,float y,int z,int tx,float ty,int tz) {
        return Math.abs(x-tx)+Math.abs(z-tz)+Math.abs(y-ty)*0.1f;
    }
    private static List<Node> result(Node node) {
        LinkedList<Node> result=new LinkedList<>();
        for(Node current=node;current!=null;current=current.parent)result.addFirst(current);
        return result;
    }
    private static List<Node> search(GroundClearance ground,int sx,float sy,int sz,int tx,float ty,int tz,int budget,int radius) {
        if(!ground.standable(sx,sy,sz))return null;
        boolean distant=Math.max(Math.abs(tx-sx),Math.abs(tz-sz))>radius;
        if(!distant && !ground.standable(tx,ty,tz))return null;
        PriorityQueue<Entry> open=new PriorityQueue<>(Comparator.comparingDouble(Entry::score).thenComparingLong(Entry::order));
        Map<Cell,Float> costs=new HashMap<>();
        Map<Cell,Boolean> walkable=new HashMap<>();
        Node start=new Node(sx,sy,sz,null),best=start;
        float bestDistance=heuristic(sx,sy,sz,tx,ty,tz);
        costs.put(new Cell(sx,Math.round(sy*2),sz),0f);
        long order=0;open.add(new Entry(start,0,bestDistance,order++));
        int expansions=0;
        while(!open.isEmpty() && expansions<Math.max(1,Math.min(4096,budget))) {
            Entry entry=open.poll();Node current=entry.node;
            if(entry.cost>costs.getOrDefault(new Cell(current.x,Math.round(current.feetY*2),current.z),Float.POSITIVE_INFINITY))continue;
            expansions++;
            if(current.x==tx && current.feetY==ty && current.z==tz)return result(current);
            float distance=heuristic(current.x,current.feetY,current.z,tx,ty,tz);
            if(distance<bestDistance){best=current;bestDistance=distance;}
            for(int[] direction:DIRECTIONS)for(float dy:new float[]{0,0.5f,1,-0.5f,-1}) {
                int x=current.x+direction[0],z=current.z+direction[1];
                float y=current.feetY+dy;
                if(Math.abs(x-sx)>radius || Math.abs(z-sz)>radius || Math.abs(y-sy)>8)continue;
                Cell cell=new Cell(x,Math.round(y*2),z);
                if(!walkable.computeIfAbsent(cell,c->ground.standable(c.x,c.y/2f,c.z)))continue;
                if(!ground.transition(current.x,current.feetY,current.z,x,y,z))continue;
                float cost=entry.cost+1f+(dy>0?0.4f:dy<0?0.15f:0f);
                if(cost>=costs.getOrDefault(cell,Float.POSITIVE_INFINITY))continue;
                costs.put(cell,cost);
                Node next=new Node(x,y,z,current);
                open.add(new Entry(next,cost,cost+heuristic(x,y,z,tx,ty,tz),order++));
            }
        }
        // Distant goals can use a bounded route segment; enclosed local goals stop safely.
        return distant && best!=start?result(best):null;
    }
}
