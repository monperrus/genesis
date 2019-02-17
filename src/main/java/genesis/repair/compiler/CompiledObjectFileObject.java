// Copyright (C) 2016 Fan Long, Peter Amidon, Martin Rianrd and MIT CSAIL 
// Genesis (A successor of Prophet for Java Programs)
// 
// This file is part of Genesis.
// 
// Genesis is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 2 of the License, or
// (at your option) any later version.
// 
// Genesis is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with Genesis.  If not, see <http://www.gnu.org/licenses/>.
package genesis.repair.compiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import javax.tools.SimpleJavaFileObject;



@SuppressWarnings("restriction")
public class CompiledObjectFileObject extends SimpleJavaFileObject {

	protected ByteArrayOutputStream byteCodes;
	
	public CompiledObjectFileObject(String qualifiedName, Kind kind) {
		super(URI.create(qualifiedName), kind);
	}
	
	public CompiledObjectFileObject(String qualifiedName, Kind kind, byte[] bytes) {
		this(qualifiedName, kind);
		setBytecodes(bytes);
	}
	
	@Override
	public InputStream openInputStream() {
		return new ByteArrayInputStream(byteCodes());
	}

	@Override
	public OutputStream openOutputStream() {
      byteCodes = new ByteArrayOutputStream();
      return byteCodes;
	}
	
	private void setBytecodes(byte[] bytes) {
		try {
			openOutputStream().write(bytes);
		} catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}
	
	public byte[] byteCodes() {
		return byteCodes.toByteArray();
	}
	
	
}
