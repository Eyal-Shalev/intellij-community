// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging

import org.apache.tools.ant.BuildException

internal class IntelliJBuildException(location: String?, message: String, cause: Throwable?)
  : BuildException(if (location.isNullOrEmpty()) message else "$location: $message", cause)