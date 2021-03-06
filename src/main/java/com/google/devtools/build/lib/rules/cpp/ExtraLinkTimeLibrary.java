// Copyright 2015 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.rules.cpp;

import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.rules.cpp.LinkerInputs.LibraryToLink;

/**
 * An extra library to include in a link.  The actual library is built
 * at link time.
 *
 * <p>This can be used for non-C++ inputs to a C++ link.  A class that
 * implements this interface will support transitively gathering all
 * inputs from link dependencies, and then combine them all together
 * into a set of C++ libraries.
 *
 * <p>Any implementations must be immutable (and therefore thread-safe),
 * because this is passed between rules and accessed in a multi-threaded
 * context.
 */
public interface ExtraLinkTimeLibrary {
  /**
   * Build the LibraryToLink inputs to pass to the C++ linker.
   */
  NestedSet<LibraryToLink> buildLibraries(RuleContext context);

  /**
   * Get a new Builder for this ExtraLinkTimeLibrary class.  This acts
   * like a static method, in that the result does not depend on the
   * current state of the object, and the new Builder starts out
   * empty.
   */
  Builder getBuilder();

  /**
   * The Builder interface builds an ExtraLinkTimeLibrary.
   */
  public interface Builder {
    /**
     * Add the inputs associated with another instance of the same
     * underlying ExtraLinkTimeLibrary type.
     */
    void addTransitive(ExtraLinkTimeLibrary dep);

    /**
     * Build the ExtraLinkTimeLibrary based on the inputs.
     */
    ExtraLinkTimeLibrary build();
  }
}
