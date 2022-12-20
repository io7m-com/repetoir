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

import com.io7m.repetoir.core.RPServiceEventType.RPServiceDeregistered;
import com.io7m.repetoir.core.RPServiceEventType.RPServiceDirectoryClosed;
import com.io7m.repetoir.core.RPServiceEventType.RPServiceDirectoryClosing;
import com.io7m.repetoir.core.RPServiceEventType.RPServiceRegistered;
import net.jcip.annotations.GuardedBy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.stream.Collectors;

/**
 * A service directory.
 */

public final class RPServiceDirectory implements RPServiceDirectoryWritableType
{
  private static final Logger LOG =
    LoggerFactory.getLogger(RPServiceDirectory.class);

  private final Object serviceLock;
  @GuardedBy("serviceLock")
  private final Map<Object, List<Object>> services;
  private final SubmissionPublisher<RPServiceEventType> events;

  /**
   * Construct a service directory.
   */

  public RPServiceDirectory()
  {
    this.serviceLock = new Object();
    this.services = new ConcurrentHashMap<>();
    this.events = new SubmissionPublisher<>();
  }

  @Override
  public <T extends RPServiceType> void register(
    final Class<T> clazz,
    final T service)
  {
    Objects.requireNonNull(clazz, "clazz");
    Objects.requireNonNull(service, "service");

    LOG.debug("register: {} → {}", clazz, service);

    synchronized (this.serviceLock) {
      final var existing =
        Optional.ofNullable(this.services.get(clazz))
          .orElse(new ArrayList<>());
      existing.add(service);
      this.services.put(clazz, existing);
    }

    this.events.submit(new RPServiceRegistered(clazz, service));
  }

  @Override
  public <T extends RPServiceType> void deregister(
    final Class<T> clazz,
    final T service)
  {
    Objects.requireNonNull(clazz, "clazz");
    Objects.requireNonNull(service, "service");

    boolean deregistered = false;
    synchronized (this.serviceLock) {
      final var existing = this.services.get(clazz);
      if (existing != null) {
        existing.remove(service);
        deregistered = true;
        if (existing.isEmpty()) {
          this.services.remove(clazz);
        }
      }
    }

    if (deregistered) {
      this.events.submit(new RPServiceDeregistered(clazz, service));
    }
  }

  @Override
  public <T extends RPServiceType> void deregisterAll(
    final Class<T> clazz)
  {
    Objects.requireNonNull(clazz, "clazz");

    final List<?> existing;
    synchronized (this.serviceLock) {
      existing = this.services.remove(clazz);
    }

    if (existing != null) {
      for (final var s : existing) {
        this.events.submit(new RPServiceDeregistered(clazz, clazz.cast(s)));
      }
    }
  }

  @Override
  public <T extends RPServiceType> Optional<T> optionalService(
    final Class<T> clazz)
  {
    Objects.requireNonNull(clazz, "clazz");

    synchronized (this.serviceLock) {
      return Optional.ofNullable(this.services.get(clazz))
        .flatMap(xs -> {
          try {
            return Optional.of(clazz.cast(xs.get(0)));
          } catch (final IndexOutOfBoundsException e) {
            return Optional.empty();
          }
        });
    }
  }

  @Override
  public <T extends RPServiceType> T requireService(
    final Class<T> clazz)
    throws RPServiceException
  {
    Objects.requireNonNull(clazz, "clazz");

    return this.optionalService(clazz)
      .orElseThrow(() -> new RPServiceException(
        String.format(
          "No implementations available of type %s",
          clazz.getCanonicalName())
      ));
  }

  @Override
  public <T extends RPServiceType> List<? extends T> optionalServices(
    final Class<T> clazz)
    throws RPServiceException
  {
    Objects.requireNonNull(clazz, "clazz");

    synchronized (this.serviceLock) {
      return Optional.ofNullable(this.services.get(clazz))
        .stream()
        .flatMap(xs -> xs.stream().map(clazz::cast))
        .collect(Collectors.toList());
    }
  }

  @Override
  public List<RPServiceType> services()
  {
    synchronized (this.serviceLock) {
      return this.services.values()
        .stream()
        .flatMap(Collection::stream)
        .map(RPServiceType.class::cast)
        .collect(Collectors.toList());
    }
  }

  @Override
  public Flow.Publisher<RPServiceEventType> events()
  {
    return this.events;
  }

  @Override
  public void close()
    throws IOException
  {
    this.events.submit(new RPServiceDirectoryClosing());

    final List<Object> allServices;
    synchronized (this.serviceLock) {
      allServices =
        this.services.values()
          .stream()
          .flatMap(Collection::stream)
          .collect(Collectors.toList());
    }

    Exception exception = null;
    for (final var service : allServices) {
      if (service instanceof AutoCloseable) {
        try {
          LOG.debug("close: {}", service);
          ((AutoCloseable) service).close();
        } catch (final Exception e) {
          if (exception == null) {
            exception = e;
          } else {
            exception.addSuppressed(e);
          }
        }
      }
    }

    this.events.submit(new RPServiceDirectoryClosed());
    this.events.close();

    if (exception != null) {
      throw new IOException(exception);
    }
  }
}
