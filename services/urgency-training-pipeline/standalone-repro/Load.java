/*
 * Compile: javac -cp deepnetts-core-1.13.2.jar Load.java
 * Run:    java -cp ".:deepnetts-core-1.13.2.jar:visrec-api-1.0.5.jar:json-20170516.jar:log4j-api-2.7.jar:log4j-core-2.7.jar:commons-configuration2-2.2.jar:commons-lang3-3.3.2.jar" Load
 * (All jars from Maven: com.deepnetts:deepnetts-core:1.13.2 + its deps)
 * Fails with: StreamCorruptedException: invalid type code: 00
 */
import deepnetts.net.FeedForwardNetwork;
import deepnetts.util.FileIO;

/** Minimal repro for StreamCorruptedException when loading .dnet file. */
public class Load {
    public static void main(String[] args) throws Exception {
        FeedForwardNetwork net = (FeedForwardNetwork) FileIO.createFromFile("model.dnet", FeedForwardNetwork.class);
        System.out.println("Loaded OK");
    }
}
