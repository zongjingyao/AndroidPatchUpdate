package cn.zjy.patchupdate;

/**
 * Created by zongjingyao on 16/10/14.
 */

public class BsPatch {
    static {
        System.loadLibrary("bspatch");
    }

    private BsPatch() {
    }

    public static native int merge(String oldFilePath, String newFilePath, String patchPatch);
}
