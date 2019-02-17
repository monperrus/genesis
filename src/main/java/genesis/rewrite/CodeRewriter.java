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
package genesis.rewrite;

import java.util.ArrayList;

import java.util.HashSet;

import genesis.GenesisException;
import genesis.node.MyCtNode;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.cu.CompilationUnit;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.support.reflect.code.CtLocalVariableImpl;
import spoon.support.reflect.code.CtReturnImpl;

public class CodeRewriter {

	MyCtNode root;
	ArrayList<MyCtNode> origNodes;
	ArrayList<MyCtNode> newNodes;
	String commentString;
	
	public CodeRewriter(MyCtNode root) {
		this.root = root;
		origNodes = new ArrayList<MyCtNode>();
		newNodes = new ArrayList<MyCtNode>();
		commentString = null;
	}

	public void setCommentString(String str) {
		commentString = str;
	}
	
	public void addMapping(MyCtNode origNode, MyCtNode newNode) {
		if (origNode.isTrait() || origNode.isReference())
			throw new GenesisException("Invalid element for rewrite, need to be element: " + origNode.toString());
		origNodes.add(origNode);
		newNodes.add(newNode);
	}

	private int getSourceStart(String origCode, MyCtNode node, HashSet<Integer> comments) {
		if (node.isCollection()) {
			int ret = -1;
			int n = node.getNumChildren();
			for (int i = 0; i < n; i++) {
				MyCtNode child = node.getChild(i);
				int v = getSourceStart(origCode, child, comments);
				// XXX: OK, this is because the spoon has a stupid bug for start position of
				// local definition, I really hate this.
				// I know this might cause another problem, but whatever
				if (!node.isColClass(CtExpression.class) && child.isEleClass(CtStatement.class)) {
					while (v > 0 && ((origCode.charAt(v) != '{' && origCode.charAt(v) != '}' && origCode.charAt(v) != ';') 
							|| comments.contains(v)))
						v--;
					if ((origCode.charAt(v) == '{' || origCode.charAt(v) == '}' || origCode.charAt(v) == ';') && !comments.contains(v))
						v ++;
					while (v < origCode.length() && (Character.isWhitespace(origCode.charAt(v)) || comments.contains(v))) v++;
				}
				if (ret == -1 || ret > v)
					ret = v;
			}
			if (ret == -1)
				throw new GenesisException("Attempt to get source start idx for an empty collection!");
			return ret;
		}
		else {
			Object o = node.getRawObject();
			assert(o instanceof CtElement);
			CtElement ele = (CtElement) o;
			SourcePosition sp = ele.getPosition();
			int v = sp.getSourceStart();
			if (ele instanceof CtStatement) {
				CtStatement st = (CtStatement) ele;
				// XXX: OK, Spoon has a bug for find the start location
				// of local variable definition... it points to the 
				// variable name not the start of the type token
				if (st instanceof CtLocalVariableImpl) {
					int v1 = v;
					while (v1 > 0 && origCode.charAt(v1) != '{' && origCode.charAt(v1) != ';' && origCode.charAt(v1) != '}' && origCode.charAt(v1) != '(')
						v1 --;
					v1 ++;
					while (v1 < v && Character.isWhitespace(origCode.charAt(v1)))
						v1 ++;
					v = v1;
				}
				// XXX: So we have some label to deal with, I hate you
				// why the spoon does not handle this???
				if (st.getLabel() != null && !st.getLabel().equals("")) {
					String label = st.getLabel();
					while (v > 0 && !origCode.substring(v, v + label.length()).equals(label))
						v--;
				}
			}
			return v;
		}
	}
	
	private int getSourceEnd(MyCtNode node) {
		if (node.isCollection()) {
			int ret = -1;
			int n = node.getNumChildren();
			for (int i = 0; i < n; i++) {
				int v = getSourceEnd(node.getChild(i));
				if (ret == -1 || ret < v)
					ret = v;
			}
			if (ret == -1)
				throw new GenesisException("Attempt to get source start idx for an empty collection!");
			return ret;
		}
		else {
			Object o = node.getRawObject();
			assert(o instanceof CtElement);
			CtElement ele = (CtElement) o;
			SourcePosition sp = ele.getPosition();
			return sp.getSourceEnd();
		}
	}
	
	private int getSourceEnd(String origCode, MyCtNode node, HashSet<Integer> comments) {
		if (node.isCollection()) {
			int v = getSourceEnd(node);
			MyCtNode parentNode = node.parentEleNode();
			if (parentNode.isEleClass(CtStatementList.class)) {
				while (v < origCode.length() - 1 && ((origCode.charAt(v) != '}' && origCode.charAt(v) != ';') || comments.contains(v)))
					v++;
				if (v < origCode.length() - 1 && origCode.charAt(v+1) == '\n')
					v++;
			}
			return v;
		}
		else {
			// XXX: This is super hacky,
			// I don't know why the ReturnImpl is special in Spoon,
			// In spoon, this just put end before ; instead of at ;
			int v = getSourceEnd(node);
			if (node.isEleClass(CtReturnImpl.class)) {
				while (v > 0 && origCode.charAt(v) != ';')
					v--;
				if (v != 0)
					v = v - 1;
			}
			return v;
		}
	}
	
	private String getIndentString(String origCode, int idx) {
		int i = idx;
		while (i > 0 && origCode.charAt(i) != '\n')
			i --;
		if (origCode.charAt(i) == '\n') i++;
		StringBuffer ret = new StringBuffer();
		while (Character.isWhitespace(origCode.charAt(i))) {
			ret.append(origCode.charAt(i));
			i++;
		}
		return ret.toString();
	}
	
	private String applyIndent(String snippet, String indentStr) {
		StringBuffer ret = new StringBuffer();
		for (int i = 0; i < snippet.length(); i++) {
			ret.append(snippet.charAt(i));
			if (snippet.charAt(i) == '\n' && i != snippet.length() - 1)
				ret.append(indentStr);
		}
		return ret.toString();
	}
	
	private String getOriginalSourceFile(MyCtNode node, MyCtNode node2) {
		CtElement ele = null;
		if (node.isCollection()) {
			if (node.getNumChildren() > 0)
				ele = (CtElement) node.getChild(0).getRawObject();
			else {
				if (node2.getNumChildren() == 0)
					throw new GenesisException("Replace an empty collection to an empty colleciton for rewrite!???");
				else
					ele = (CtElement) node2.getChild(0).getRawObject();
			}
		}
		else
			ele = (CtElement) node.getRawObject();
		SourcePosition sp = ele.getPosition();
		if (sp == null)
			throw new GenesisException("Unable to get the position of the element: " + ele.toString());
		CompilationUnit cu = sp.getCompilationUnit();
		if (cu == null)
			throw new GenesisException("Unable to get the compilation unit of the element: " + ele.toString());
		return cu.getOriginalSourceCode();
	}
	
	private HashSet<Integer> getCommentIndexSet(String code) {
		boolean inLineComment = false;
		boolean inComment = false;
		HashSet<Integer> ret = new HashSet<Integer>();
		for (int i = 0; i < code.length(); i++) {
			if (!inComment && !inLineComment && i < code.length() - 1) {
				if (code.charAt(i) == '/' && code.charAt(i + 1) == '/') {
					inLineComment = true;
					ret.add(i);
					ret.add(i+1);
					i ++;
				}
				else if (code.charAt(i) == '/' && code.charAt(i + 1) == '*') {
					inComment = true;
					ret.add(i);
					ret.add(i+1);
					i ++;
				}
			}
			else if (inComment && i < code.length() - 1) {
				if (code.charAt(i) == '*' && code.charAt(i + 1) == '/') {
					inComment = false;
					ret.add(i);
					ret.add(i+1);
					i ++;
				}
				else
					ret.add(i);
			}
			else if (inLineComment) {
				if (code.charAt(i) == '\n') {
					inLineComment = false;
				}
				ret.add(i);
			}
		}
		return ret;
	}
	
	public String rewrite() {
		if (origNodes.size() == 0)
			throw new GenesisException("No mapping supplied for rewrite()!");
		String origCode = getOriginalSourceFile(origNodes.get(0), newNodes.get(0));
		HashSet<Integer> commentIdxs = getCommentIndexSet(origCode);
		
		ArrayList<Integer> starts = new ArrayList<Integer>();
		ArrayList<Integer> ends = new ArrayList<Integer>();
		ArrayList<String> indentStrs = new ArrayList<String>();
		for (int i = 0; i < origNodes.size(); i++) {
			MyCtNode orig = origNodes.get(i);
			starts.add(getSourceStart(origCode, orig, commentIdxs));
			ends.add(getSourceEnd(origCode, orig, commentIdxs));
			indentStrs.add(getIndentString(origCode, starts.get(i)));
		}
		
		int n = starts.size();
		for (int i = 0; i < n; i++)
			for (int j = i + 1; j < n; j++)
				if (starts.get(i) > starts.get(j)) {
					int tmp = starts.get(i);
					starts.set(i, starts.get(j));
					starts.set(j, tmp);
					tmp = ends.get(i);
					ends.set(i, ends.get(j));
					ends.set(j, tmp);
					MyCtNode tmp2 = newNodes.get(i);
					newNodes.set(i, newNodes.get(j));
					newNodes.set(j, tmp2);
				}
		
		StringBuffer ret = new StringBuffer();
		int lastPos = 0;
		for (int i = 0; i < n; i++) {
			if (starts.get(i) < lastPos) {
				throw new GenesisException("Rewrite failed! Intersecting with each other!");
			}
			if (starts.get(i) > lastPos) {
				ret.append(origCode.substring(lastPos, starts.get(i)));
			}
			String newCodeStrSnippet = newNodes.get(i).codeString(origNodes.get(i));
			// This handles the case where we need to add a semicolon 
			if (origNodes.get(i).isEleClass(CtStatement.class) && newNodes.get(i).isEleClass(CtStatement.class)) {
				boolean needSep = false;
				int j = ends.get(i);
				while (j >= starts.get(i) && Character.isWhitespace(origCode.charAt(j)))
					j --;
				if (j >= starts.get(i)) {
					char ch = origCode.charAt(j);
					needSep = (ch == '}') || (ch == '{') || (ch == ';');
				}
				if (needSep) {
					j = newCodeStrSnippet.length() - 1;
					while (j >= 0 && Character.isWhitespace(newCodeStrSnippet.charAt(j)))
						j --;
					if (j >= 0) {
						char ch = newCodeStrSnippet.charAt(j);
						if ((ch != '}') && (ch != '{') && (ch != ';'))
							newCodeStrSnippet += ";";
					}
				}
			}
			if (commentString == null)
				ret.append(applyIndent(newCodeStrSnippet, indentStrs.get(i)));
			else
				ret.append(applyIndent("/*" + commentString + "*/\n" + newCodeStrSnippet, indentStrs.get(i)));
			lastPos = ends.get(i) + 1;
			// This handles the case where we need to remove the next semicolon
			/*int j = newCodeStrSnippet.length() - 1;
			while (j >= 0 && Character.isWhitespace(newCodeStrSnippet.charAt(j)))
				j --;
			if (j >= 0) {
				char ch = newCodeStrSnippet.charAt(j);
				if ((ch == '}') || (ch == '{') || (ch == ';')) {
					j = lastPos;
					while (j >= 0 && Character.isWhitespace(origCode.charAt(j)))
						j --;
					if (j >= 0) {
						ch = origCode.charAt(j);
						if (ch == ';')
							lastPos = j + 1;
					}
				}
			}*/
		}
		if (lastPos != origCode.length())
			ret.append(origCode.substring(lastPos));
		
		return removeTrailingSpaces(ret);
	}

	private String removeTrailingSpaces(StringBuffer buf) {
		StringBuffer ret = new StringBuffer();
		String whiteBuf = "";
		for (int i = 0; i < buf.length(); i++) {
			char ch = buf.charAt(i);
			if (Character.isWhitespace(ch) && ch != '\n')
				whiteBuf += ch;
			else if (ch == '\n') {
				whiteBuf = "";
				ret.append(ch);
			}
			else {
				if (whiteBuf.length() != 0) {
					ret.append(whiteBuf);
					whiteBuf = "";
				}
				ret.append(ch);	
			}
		}
		return ret.toString();
	}
}
