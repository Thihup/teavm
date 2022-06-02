/*
 *  Copyright 2022 Alexey Andreev.
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
package org.teavm.backend.javascript.splitting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import org.teavm.callgraph.CallGraph;
import org.teavm.callgraph.CallGraphNode;
import org.teavm.callgraph.CallSite;
import org.teavm.model.MethodReference;

public class RegionAnalyzer {
    private CallGraph callGraph;
    private Predicate<MethodReference> predicate;
    private Map<CallGraphNode, NodeInfo> nodeInfoMap = new LinkedHashMap<>();
    private Queue<Step> queue = new ArrayDeque<>();
    private List<Region> regions = new ArrayList<>();
    private List<Step> deferredSteps = new ArrayList<>();
    private Map<BitSet, Part> parts = new LinkedHashMap<>();

    public RegionAnalyzer(CallGraph callGraph, Predicate<MethodReference> predicate) {
        this.callGraph = callGraph;
        this.predicate = predicate;
    }

    public void analyze(MethodReference entryPoint) {
        mark(entryPoint);
        buildParts();
    }

    public Part getPart(MethodReference method) {
        CallGraphNode node = callGraph.getNode(method);
        if (node == null) {
            return null;
        }
        NodeInfo info = nodeInfoMap.get(node);
        return info != null ? info.mainPart : null;
    }

    private void mark(MethodReference entryPoint) {
        CallGraphNode node = callGraph.getNode(entryPoint);
        if (node == null) {
            return;
        }

        Region firstRegion = createRegion(null);
        NodeInfo firstInfo = getInfo(node);
        firstInfo.startingRegion = firstRegion;
        deferredSteps.add(new Step(firstInfo, firstRegion));

        while (!deferredSteps.isEmpty()) {
            queue.addAll(deferredSteps);
            for (Step step : deferredSteps) {
                step.nodeInfo.deferred = false;
            }
            deferredSteps.clear();
            processQueue();
        }
    }

    private void processQueue() {
        while (!queue.isEmpty()) {
            Step step = queue.remove();
            if (step.nodeInfo.deferred || step.region.predecessors.intersects(step.nodeInfo.regions)) {
                continue;
            }
            step.nodeInfo.regions.set(step.region.id);

            CallGraphNode cgNode = step.nodeInfo.node;
            for (CallSite callSite : cgNode.getCallSites()) {
                for (CallGraphNode calledNode : callSite.getCalledMethods()) {
                    NodeInfo calledNodeInfo = getInfo(calledNode);
                    if (calledNodeInfo.deferred || step.region.predecessors.intersects(calledNodeInfo.regions)) {
                        continue;
                    }

                    if (predicate.test(calledNode.getMethod())) {
                        Region newRegion = createRegion(step.region);
                        Step nextStep = new Step(calledNodeInfo, createRegion(step.region));
                        calledNodeInfo.deferred = true;
                        deferredSteps.add(nextStep);
                    } else {
                        queue.add(new Step(calledNodeInfo, step.region));
                    }
                }
            }
        }
    }

    private void buildParts() {
        for (NodeInfo nodeInfo : nodeInfoMap.values()) {
            Part part = parts.computeIfAbsent(nodeInfo.regions, Part::new);
            for (int i = nodeInfo.regions.nextSetBit(0); i >= 0; i = nodeInfo.regions.nextSetBit(i + 1)) {
                regions.get(i).parts.add(part);
            }
            nodeInfo.regions = null;
            nodeInfo.mainPart = part;
        }
    }

    private NodeInfo getInfo(CallGraphNode node) {
        return nodeInfoMap.computeIfAbsent(node, NodeInfo::new);
    }

    private Region createRegion(Region parent) {
        Region region = new Region(regions.size(), parent);
        regions.add(region);
        return region;
    }

    static class NodeInfo {
        CallGraphNode node;
        Region startingRegion;
        BitSet regions = new BitSet();
        Part mainPart;
        boolean deferred;

        NodeInfo(CallGraphNode node) {
            this.node = node;
        }
    }

    static class Step {
        NodeInfo nodeInfo;
        Region region;

        Step(NodeInfo nodeInfo, Region region) {
            this.nodeInfo = nodeInfo;
            this.region = region;
        }
    }

    static class Region {
        int id;
        Region parent;
        final BitSet predecessors = new BitSet();
        Set<Part> parts = new HashSet<>();

        Region(int id, Region parent) {
            this.id = id;
            this.parent = parent;
            if (parent != null) {
                predecessors.or(parent.predecessors);
            }
            predecessors.set(id);
        }
    }

    public static class Part {
        BitSet regions;

        Part(BitSet regions) {
            this.regions = regions;
        }
    }
}
