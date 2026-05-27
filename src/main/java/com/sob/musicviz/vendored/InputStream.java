/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.sob.musicviz.vendored;

import java.io.IOException;
import java.nio.ByteBuffer;

public class InputStream extends java.io.InputStream
{
	private final ByteBuffer buffer;

	public InputStream(byte[] buffer)
	{
		this.buffer = ByteBuffer.wrap(buffer);
	}

	public byte[] getArray()
	{
		assert buffer.hasArray();
		return buffer.array();
	}

	public void skip(int length)
	{
		int pos = buffer.position();
		pos += length;
		buffer.position(pos);
	}

	public void setOffset(int offset)
	{
		buffer.position(offset);
	}

	public int getOffset()
	{
		return buffer.position();
	}

	public int getLength()
	{
		return buffer.limit();
	}

	public byte readByte()
	{
		return buffer.get();
	}

	public int readUnsignedByte()
	{
		return this.readByte() & 0xFF;
	}

	public int readUnsignedShort()
	{
		return buffer.getShort() & 0xFFFF;
	}

	public int readVarInt()
	{
		byte var1 = this.readByte();

		int var2;
		for (var2 = 0; var1 < 0; var1 = this.readByte())
		{
			var2 = (var2 | var1 & 127) << 7;
		}

		return var2 | var1;
	}

	@Override
	public int read() throws IOException
	{
		return this.readUnsignedByte();
	}
}
