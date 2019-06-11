package com.intel.hpnl.core;

import java.nio.ByteBuffer;

public class HpnlBuffer {
  public final static byte TYPE_MSG = 0;
  public final static byte TYPE_RDM = 1;
	
  public HpnlBuffer(int bufferId, ByteBuffer byteBuffer) {
    this.bufferId = bufferId;
    this.byteBuffer = byteBuffer;
  }

  public HpnlBuffer(int bufferId, ByteBuffer byteBuffer, long rkey) {
    this.bufferId = HpnlBufferId;
    this.byteBuffer = byteBuffer;
    this.rkey = rkey;
  }

  public HpnlBuffer(int bufferId, ByteBuffer byteBuffer, long rkey, long address) {
    this.bufferId = bufferId;
    this.byteBuffer = byteBuffer;
    this.rkey = rkey;
    this.address = address;
  }

  public int getBufferId() {
    return this.bufferId;
  }

  public byte getType() {
    return this.type;
  }

  public long getSeq() {
    return this.seq; 
  }

  public ByteBuffer getRawBuffer() {
    return this.byteBuffer;
  }

  public long getRKey() {
    return this.rkey;
  }

  public long getAddress() {
    return address;
  }

  public int remaining() {
    return this.byteBuffer.remaining();
  }

  private void putMetadata(int srcSize, byte type, long seq) {
    byteBuffer.rewind();
    byteBuffer.limit(METADATA_SIZE+srcSize+1);
    byteBuffer.put(type);
    byteBuffer.putLong(seq);
  }
  
  private void putMetadata(int srcSize, int nameLength, ByteBuffer name, byte type, long seq) {
    byteBuffer.rewind();
    byteBuffer.limit(METADATA_SIZE+srcSize+nameLength+4+1);
    byteBuffer.put(TYPE_RDM);
    byteBuffer.putInt(nameLength);
    byteBuffer.put(name.slice());
    byteBuffer.put(type);
    byteBuffer.putLong(seq);
  }

  public void put(ByteBuffer src, byte type, long seq) {
    putMetadata(src.remaining(), type, seq);
    byteBuffer.put(src.slice());
    byteBuffer.flip();
  }
  
  public void put(ByteBuffer src, int nameLength, ByteBuffer name, byte type, long seq) {
    putMetadata(src.remaining(), nameLength, name, type, seq);
    byteBuffer.put(src.slice());
    byteBuffer.flip();
  }

  public ByteBuffer get(int blockBufferSize) {
    byteBuffer.position(0); 
    byteBuffer.limit(blockBufferSize);
    if (byteBuffer.get() == TYPE_MSG) {
      this.type = byteBuffer.get();
      this.seq = byteBuffer.getLong();
      return byteBuffer.slice();
    } else {
      this.nameLength = byteBuffer.getInt();
      byte[] nameTmp = new byte[this.nameLength];
      byteBuffer.get(nameTmp, 0, this.nameLength);
      if (this.name == null) {
        this.name = ByteBuffer.allocateDirect(this.nameLength);
      }
      this.name.put(nameTmp);
      this.name.flip();
      this.type = byteBuffer.get();
      this.seq = byteBuffer.getLong();
      return byteBuffer.slice();
    }
  }

  public int getWritableBytes(){
    return this.byteBuffer.capacity() - getMetadataSize();
  }

  public static int getMetadataSize(){
    return METADATA_SIZE;
  }

  private int HpnlBufferId;
  private byte type;
  private long seq;
  private ByteBuffer byteBuffer;
  private int nameLength;
  private ByteBuffer name;
  private long rkey;
  private long address;
  private static int METADATA_SIZE = 9;
}
