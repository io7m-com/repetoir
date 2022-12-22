/*
 * Copyright © 2022 Mark Raynsford <code@io7m.com> https://www.io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */


package com.io7m.repetoir.core;

import java.util.Objects;

/**
 * The type of service directory events.
 */

public sealed interface RPServiceEventType
{
  /**
   * A service was registered.
   *
   * @param serviceType The type under which the service is registered
   * @param instance    The service instance
   */

  record RPServiceRegistered(
    Class<? extends RPServiceType> serviceType,
    RPServiceType instance)
    implements RPServiceEventType
  {
    /**
     * A service was registered.
     */

    public RPServiceRegistered
    {
      Objects.requireNonNull(serviceType, "serviceType");
      Objects.requireNonNull(instance, "instance");
    }

    @Override
    public String toString()
    {
      return "[RPServiceRegistered %s %s]"
        .formatted(this.serviceType.getName(), this.instance);
    }
  }

  /**
   * A service was deregistered.
   *
   * @param serviceType The type under which the service is registered
   * @param instance    The service instance
   */

  record RPServiceDeregistered(
    Class<? extends RPServiceType> serviceType,
    RPServiceType instance)
    implements RPServiceEventType
  {
    /**
     * A service was deregistered.
     */

    public RPServiceDeregistered
    {
      Objects.requireNonNull(serviceType, "serviceType");
      Objects.requireNonNull(instance, "instance");
    }

    @Override
    public String toString()
    {
      return "[RPServiceDeregistered %s %s]"
        .formatted(this.serviceType.getName(), this.instance);
    }
  }

  /**
   * The service directory is about to be closed.
   */

  record RPServiceDirectoryClosing()
    implements RPServiceEventType
  {
    @Override
    public String toString()
    {
      return "[RPServiceDirectoryClosing]";
    }
  }

  /**
   * The service directory has finished closing.
   */

  record RPServiceDirectoryClosed()
    implements RPServiceEventType
  {
    @Override
    public String toString()
    {
      return "[RPServiceDirectoryClosed]";
    }
  }
}
