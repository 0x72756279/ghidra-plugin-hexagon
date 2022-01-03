/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.app.plugin.core.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ghidra.framework.options.Options;
import ghidra.program.database.ProgramBuilder;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.UniqueAddressFactory;
import ghidra.program.model.lang.ParallelInstructionLanguageHelper;
import ghidra.program.model.lang.Register;
import ghidra.program.model.lang.UnknownInstructionException;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.InstructionIterator;
import ghidra.program.model.listing.Program;
import ghidra.program.model.pcode.PcodeOp;
import ghidra.test.AbstractGhidraHeadedIntegrationTest;
import ghidra.test.TestEnv;

public class HexagonPacketTestDisassembly extends AbstractGhidraHeadedIntegrationTest {

	private TestEnv env;

	private Program program;

	@Before
	public void setUp() throws Exception {
		env = new TestEnv();
	}

	@After
	public void tearDown() {
		if (program != null)
			env.release(program);
		program = null;
		env.dispose();
	}

	protected void setAnalysisOptions(String optionName) {
		int txId = program.startTransaction("Analyze");
		Options analysisOptions = program.getOptions(Program.ANALYSIS_PROPERTIES);
		analysisOptions.setBoolean(optionName, false);
		program.endTransaction(txId, true);
	}

	void printInstructions() {
		ParallelInstructionLanguageHelper parallelHelper = program.getLanguage().getParallelInstructionHelper();
		InstructionIterator iter = program.getListing().getInstructions(true);
		while (iter.hasNext()) {
			Instruction instr = iter.next();
			String prefix = parallelHelper.getMnemonicPrefix(instr);
			if (prefix == null) {
				prefix = " ";
			}
			String suffix = parallelHelper.getMnemonicSuffix(instr);
			if (suffix == null) {
				suffix = " ";
			}

			String out = "";
			out += instr.getAddress();
			out += " ";
			out += prefix;
			out += " ";
			out += instr;
			out += " ";
			out += suffix;

			System.out.println(out);
		}
	}

	@Test
	public void testSingleInstructionPackets() throws Exception {
		ProgramBuilder programBuilder = new ProgramBuilder("Test", "hexagon:LE:32:default");
		program = programBuilder.getProgram();
		int txId = program.startTransaction("Add Memory");
		programBuilder.createMemory(".text", "1000", 12);
		programBuilder.setBytes("1000", "01 c0 9d a0 00 e0 00 78 1e c0 1e 96");

		programBuilder.disassemble("1000", 12, true);
		programBuilder.analyze();

		program.endTransaction(txId, true);

		printInstructions();

		assertEquals(3, program.getListing().getNumInstructions());
	}

	@Test
	public void testMultipleInstructionPackets() throws Exception {
		ProgramBuilder programBuilder = new ProgramBuilder("Test", "hexagon:LE:32:default");
		program = programBuilder.getProgram();
		int txId = program.startTransaction("Add Memory");
		programBuilder.createMemory(".text", "1000", 8);
		programBuilder.setBytes("1000", "01 41 01 f3 00 c0 80 52");

		programBuilder.disassemble("1000", 8, true);
		programBuilder.analyze();

		program.endTransaction(txId, true);

		printInstructions();

		assertEquals(2, program.getListing().getNumInstructions());
	}

	@Test
	public void testDuplexInstruction() throws Exception {
		ProgramBuilder programBuilder = new ProgramBuilder("Test", "hexagon:LE:32:default");
		program = programBuilder.getProgram();
		int txId = program.startTransaction("Add Memory");
		programBuilder.createMemory(".text", "1000", 4);
		programBuilder.setBytes("1000", "c0 3f 10 48");

		programBuilder.disassemble("1000", 4, true);
		programBuilder.analyze();

		program.endTransaction(txId, true);

		printInstructions();

		assertEquals(2, program.getListing().getNumInstructions());
	}

	@Test
	public void testImmext() throws Exception {
		ProgramBuilder programBuilder = new ProgramBuilder("Test", "hexagon:LE:32:default");
		program = programBuilder.getProgram();
		int txId = program.startTransaction("Add Memory");
		programBuilder.createMemory(".text", "1000", 8);
		programBuilder.setBytes("1000", "d1 48 01 00 16 c0 41 3c");

		programBuilder.disassemble("1000", 8, true);
		programBuilder.analyze();

		program.endTransaction(txId, true);

		printInstructions();

		assertEquals(2, program.getListing().getNumInstructions());

		Instruction extended = program.getListing().getInstructionAt(programBuilder.addr("1004"));
		assertEquals(extended.toString(), "S4_storeiri_io R1 0x0 0x123456");
	}

	@Test
	public void testImmextScale() throws Exception {
		ProgramBuilder programBuilder = new ProgramBuilder("Test", "hexagon:LE:32:default");
		program = programBuilder.getProgram();
		int txId = program.startTransaction("Add Memory");
		programBuilder.createMemory(".text", "1000", 12);
		programBuilder.setBytes("1000", "8f 62 fe 0f 58 40 20 5d 80 d5 14 fd");

		programBuilder.disassemble("1000", 12, true);
		programBuilder.analyze();

		program.endTransaction(txId, true);

		printInstructions();

		assertEquals(3, program.getListing().getNumInstructions());

		// See section 10.9 in "Qualcomm Hexagon V66 Programmer's Reference Manual"
		// > When constant extenders are used, scaled immediates are not scaled by
		// > the processor
		Instruction extended = program.getListing().getInstructionAt(programBuilder.addr("1004"));
		assertEquals(extended.toString(), "J2_callf P0 0xffe8b3ec");
	}

	@Test
	public void testImmextBeforeDuplex() throws Exception {
		ProgramBuilder programBuilder = new ProgramBuilder("Test", "hexagon:LE:32:default");
		program = programBuilder.getProgram();
		int txId = program.startTransaction("Add Memory");
		programBuilder.createMemory(".text", "1000", 12);

		programBuilder.setBytes("1000", "b4 67 0f 0c d2 29 41 29");

		programBuilder.disassemble("1000", 12, true);
		programBuilder.analyze();

		program.endTransaction(txId, true);

		printInstructions();

		assertEquals(4, program.getListing().getNumInstructions());

		// See section 10.3 in "Qualcomm Hexagon V66 Programmer's Reference Manual"
		// > Note that a duplex can contain only one constant-extended
		// > instruction, and it must > appear in the Slot 1 position.
		Instruction duplex_immext = program.getListing().getInstructionAt(programBuilder.addr("1004"));
		assertEquals(duplex_immext.toString(), "SA1_seti R2 0x1d");
		duplex_immext = program.getListing().getInstructionAt(programBuilder.addr("1006"));
		assertEquals(duplex_immext.toString(), "SA1_seti R1 0xc0f9ed14");
	}

	@Test
	public void testDotNewPredicates() throws Exception {
		ProgramBuilder programBuilder = new ProgramBuilder("Test", "hexagon:LE:32:default");
		program = programBuilder.getProgram();
		int txId = program.startTransaction("Add Memory");
		programBuilder.createMemory(".text", "1000", 24);
		programBuilder.setBytes("1000", "08 62 03 10 0a 62 03 12 01 c1 01 f3 c0 3f 00 48 c0 3f 10 48 c0 3f 20 48");

		programBuilder.disassemble("1000", 24, true);
		programBuilder.analyze();

		program.endTransaction(txId, true);

		printInstructions();

		assertEquals(9, program.getListing().getNumInstructions());
	}

	@Test
	public void testNewValueOperands() throws Exception {
		ProgramBuilder programBuilder = new ProgramBuilder("Test", "hexagon:LE:32:default");
		program = programBuilder.getProgram();
		int txId = program.startTransaction("Add Memory");
		programBuilder.createMemory(".text", "1000", 12);
		programBuilder.setBytes("1000", "03 42 01 f3 00 d2 a5 a1 00 c0 9f 52");

		programBuilder.disassemble("1000", 12, true);
		programBuilder.analyze();

		program.endTransaction(txId, true);

		printInstructions();

		Instruction newvalue_operand = program.getListing().getInstructionAt(programBuilder.addr("1004"));
		assertEquals(newvalue_operand.toString(), "S2_storerinew_io R5 R3 0x0");
	}

	// Challenge here is that both R4 and R3 are written, but only R4 is only
	// written (R3 is both read and written)
	// The analyzer should choose R4
	@Test
	public void testNewValueOperandsTwoRegsWritten() throws Exception {
		ProgramBuilder programBuilder = new ProgramBuilder("Test", "hexagon:LE:32:default");
		program = programBuilder.getProgram();
		int txId = program.startTransaction("Add Memory");
		programBuilder.createMemory(".text", "1000", 8);
		programBuilder.setBytes("1000", "24 40 03 9b 08 c2 a0 ab");

		programBuilder.disassemble("1000", 8, true);
		programBuilder.analyze();

		program.endTransaction(txId, true);

		printInstructions();
	}

	@Test
	public void testEndloop0() throws Exception {
		ProgramBuilder programBuilder = new ProgramBuilder("Test", "hexagon:LE:32:default");
		program = programBuilder.getProgram();
		int txId = program.startTransaction("Add Memory");
		programBuilder.createMemory(".text", "1000", 20);

		programBuilder.setBytes("1000", "52 40 00 69 01 c0 00 78 21 80 01 b0 00 c0 00 7f 00 c0 9f 52");

		programBuilder.disassemble("1000", 20, true);
		programBuilder.analyze();

		program.endTransaction(txId, true);

		printInstructions();

		Instruction endloop = program.getListing().getInstructionAt(programBuilder.addr("100c"));
		ParallelInstructionLanguageHelper parallelHelper = program.getLanguage().getParallelInstructionHelper();
		assertEquals(parallelHelper.getMnemonicSuffix(endloop), "}:endloop0");
	}

}
