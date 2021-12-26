/* ###
 * IP: GHIDRA
 * REVIEWED: YES
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

import ghidra.program.model.address.UniqueAddressFactory;
import ghidra.program.model.lang.InstructionContext;
import ghidra.program.model.lang.PackedBytes;
import ghidra.program.model.lang.ParallelInstructionLanguageHelper;
import ghidra.program.model.lang.UnknownInstructionException;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.Program;

public class HexagonParallelInstructionLanguageHelper implements ParallelInstructionLanguageHelper {

	@Override
	public String getMnemonicPrefix(Instruction instr) {
		Program program = instr.getProgram();
		HexagonAnalysisState state = HexagonAnalysisState.getState(program);
		return state.getMnemonicPrefix(instr);
	}

	@Override
	public boolean isEndOfParallelInstructionGroup(Instruction instruction) {
		Program program = instruction.getProgram();
		HexagonAnalysisState state = HexagonAnalysisState.getState(program);
		return state.isEndOfParallelInstructionGroup(instruction);
	}

	@Override
	public PackedBytes getPcodePacked(Program program, InstructionContext context, UniqueAddressFactory uniqueFactory)
			throws UnknownInstructionException {
		HexagonAnalysisState state = HexagonAnalysisState.getState(program);
		return state.getPcodePacked(context, uniqueFactory);
	}

}