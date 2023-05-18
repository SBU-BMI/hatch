package edu.stonybrook.bmi.hatch;

/**
 *
 * @author erich
 */
public class Bug2022 {
    static byte FF = (byte) 0xff;
    static byte D9 = (byte) 0xd9;
    
    public static void cool(byte[] a) {
        a[0] = (byte) 0xFF;
        //a[1] = (byte) 0xD9;
        if (Byte.compare(a[0], FF)==0) {
            System.out.println("YAY : "+Integer.toHexString(a[0]));
        } else {
            System.out.println("UGH : "+Integer.toHexString(a[0]));
        }        
    }
    
    public static void main(String[] args) {
        byte[] a = new byte[324000];
        cool(a);
    }
}
