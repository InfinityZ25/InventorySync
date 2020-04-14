package me.infinityz.inventorysync;

public class Vector {
    public int x, z;

    public Vector(int x, int z){
        this.x = x;
        this.z = z;
    }

    public boolean isSimilar(int x, int z){
        return this.x == x && this.z == z;
    }
}
