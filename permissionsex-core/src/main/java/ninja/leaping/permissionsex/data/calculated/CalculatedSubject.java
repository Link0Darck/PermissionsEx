/**
 * PermissionsEx
 * Copyright (C) zml and PermissionsEx contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ninja.leaping.permissionsex.data.calculated;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import ninja.leaping.permissionsex.PermissionsEx;
import ninja.leaping.permissionsex.data.Caching;
import ninja.leaping.permissionsex.data.ImmutableSubjectData;
import ninja.leaping.permissionsex.util.NodeTree;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static java.util.Map.Entry;

/**
 * This is a holder that maintains the current subject data state
 */
public class CalculatedSubject implements Caching<ImmutableSubjectData> {
    private final SubjectDataBaker baker;
    private final Map.Entry<String, String> identifier;
    private final PermissionsEx pex;

    private final LoadingCache<Set<Map.Entry<String, String>>, BakedSubjectData> data = CacheBuilder.newBuilder().maximumSize(5)
            .build(new CacheLoader<Set<Map.Entry<String, String>>, BakedSubjectData>() {
        @Override
        public BakedSubjectData load(Set<Map.Entry<String, String>> contexts) throws Exception {
            return baker.bake(CalculatedSubject.this, contexts);
        }
    });

    public CalculatedSubject(SubjectDataBaker baker, Map.Entry<String, String> identifier, PermissionsEx pex) {
        this.baker = Preconditions.checkNotNull(baker, "baker");
        this.identifier = Preconditions.checkNotNull(identifier, "identifier");
        this.pex = Preconditions.checkNotNull(pex, "pex");
    }

    public Map.Entry<String, String> getIdentifier() {
        return identifier;
    }

    PermissionsEx getManager() {
        return pex;
    }

    public NodeTree getPermissions(Set<Map.Entry<String, String>> contexts) {
        Preconditions.checkNotNull(contexts, "contexts");
        try {
            return data.get(contexts).getPermissions();
        } catch (ExecutionException e) {
            return NodeTree.of(Collections.<String, Integer>emptyMap());
        }
    }

    public Map<String, String> getOptions(Set<Map.Entry<String, String>> contexts) {
        Preconditions.checkNotNull(contexts, "contexts");
        try {
            return data.get(contexts).getOptions();
        } catch (ExecutionException e) {
            return ImmutableMap.of();
        }
    }

    public List<Map.Entry<String, String>> getParents(Set<Map.Entry<String, String>> contexts) {
        Preconditions.checkNotNull(contexts, "contexts");
        try {
            return data.get(contexts).getParents();
        } catch (ExecutionException e) {
            return ImmutableList.of();
        }
    }

    public Set<Set<Map.Entry<String, String>>> getActiveContexts() {
        return data.asMap().keySet();
    }

    public int getPermission(Set<Entry<String, String>> contexts, String permission) {
        return getPermissions(contexts).get(permission);
    }

    public Optional<String> getOption(Set<Entry<String, String>> contexts, String option) {
        return Optional.fromNullable(getOptions(contexts).get(option));
    }

    public ListenableFuture<ImmutableSubjectData> update(Function<ImmutableSubjectData, ImmutableSubjectData> func) {
        return this.pex.getSubjects(this.identifier.getKey()).doUpdate(this.identifier.getValue(), func);
    }

    public ListenableFuture<ImmutableSubjectData> updateTransient(Function<ImmutableSubjectData, ImmutableSubjectData> func) {
        return this.pex.getSubjects(this.identifier.getKey()).doUpdate(this.identifier.getValue(), func);
    }

    @Override
    public void clearCache(ImmutableSubjectData newData) {
        data.invalidateAll();
        for (CalculatedSubject subject : pex.getActiveCalculatedSubjects()) {
            for (Set<Map.Entry<String, String>> ent : subject.getActiveContexts()) {
                if (subject.getParents(ent).contains(this.identifier)) {
                    subject.data.invalidateAll();
                    break;
                }
            }
        }
    }
}
