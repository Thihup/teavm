/*
 *  Copyright 2016 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.wasm.render;

import org.teavm.wasm.model.WasmType;
import org.teavm.wasm.model.expression.WasmDefaultExpressionVisitor;
import org.teavm.wasm.model.expression.WasmIndirectCall;

class WasmSignatureCollector extends WasmDefaultExpressionVisitor {
    WasmRenderingVisitor renderingVisitor;

    public WasmSignatureCollector(WasmRenderingVisitor renderingVisitor) {
        this.renderingVisitor = renderingVisitor;
    }

    @Override
    public void visit(WasmIndirectCall expression) {
        WasmType[] types = new WasmType[expression.getParameterTypes().size() + 1];
        types[0] = expression.getReturnType();
        for (int i = 0; i < expression.getParameterTypes().size(); ++i) {
            types[i + 1] = expression.getParameterTypes().get(i);
        }

        renderingVisitor.getSignatureIndex(new WasmSignature(types));
    }
}
