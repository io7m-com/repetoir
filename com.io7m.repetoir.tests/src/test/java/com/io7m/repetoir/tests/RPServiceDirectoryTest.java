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

package com.io7m.repetoir.tests;

import com.io7m.repetoir.core.RPServiceDirectory;
import com.io7m.repetoir.core.RPServiceEventType;
import com.io7m.repetoir.core.RPServiceEventType.RPServiceDeregistered;
import com.io7m.repetoir.core.RPServiceEventType.RPServiceDirectoryClosed;
import com.io7m.repetoir.core.RPServiceEventType.RPServiceDirectoryClosing;
import com.io7m.repetoir.core.RPServiceEventType.RPServiceRegistered;
import com.io7m.repetoir.core.RPServiceException;
import com.io7m.repetoir.core.RPServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class RPServiceDirectoryTest
{
  private ConcurrentLinkedDeque<RPServiceEventType> events;

  private static final class FakeService
    implements RPServiceType
  {
    @Override
    public String description()
    {
      return "Fake service";
    }
  }

  private static final class CrashClosedService
    implements Closeable, RPServiceType
  {
    @Override
    public String description()
    {
      return "Crash closed service";
    }

    @Override
    public void close()
      throws IOException
    {
      throw new IOException("Cannot close!");
    }
  }

  @BeforeEach
  public void setup()
  {
    this.events = new ConcurrentLinkedDeque<>();
  }

  private static final class PerpetualSubscriber
    implements Flow.Subscriber<RPServiceEventType>
  {
    private final ConcurrentLinkedDeque<RPServiceEventType> events;
    private Flow.Subscription subscription;

    PerpetualSubscriber(
      final ConcurrentLinkedDeque<RPServiceEventType> inEvents)
    {
      this.events = Objects.requireNonNull(inEvents, "events");
    }

    @Override
    public void onSubscribe(
      final Flow.Subscription inSubscription)
    {
      this.subscription = inSubscription;
      this.subscription.request(1L);
    }

    @Override
    public void onNext(
      final RPServiceEventType item)
    {
      this.events.add(item);
      this.subscription.request(1L);
    }

    @Override
    public void onError(
      final Throwable throwable)
    {

    }

    @Override
    public void onComplete()
    {

    }
  }

  /**
   * Retrieving a registered service works.
   *
   * @throws Exception On errors
   */

  @Test
  public void testRegisterGet()
    throws Exception
  {
    final var s = new RPServiceDirectory();
    s.events().subscribe(new PerpetualSubscriber(this.events));

    assertThrows(RPServiceException.class, () -> {
      s.requireService(FakeService.class);
    });

    final var f = new FakeService();
    s.register(FakeService.class, f);
    assertEquals(f, s.requireService(FakeService.class));
    assertEquals(f, s.optionalService(FakeService.class).orElseThrow());

    assertEquals(List.of(f), s.services());

    s.close();

    assertInstanceOf(RPServiceRegistered.class, this.events.poll());
    assertInstanceOf(RPServiceDirectoryClosing.class, this.events.poll());
    assertInstanceOf(RPServiceDirectoryClosed.class, this.events.poll());
    assertEquals(0, this.events.size());
  }

  /**
   * Retrieving registered services works.
   *
   * @throws Exception On errors
   */

  @Test
  public void testRegisterGetMultiple()
    throws Exception
  {
    final var s = new RPServiceDirectory();
    s.events().subscribe(new PerpetualSubscriber(this.events));

    assertThrows(RPServiceException.class, () -> {
      s.requireService(FakeService.class);
    });

    final var f0 = new FakeService();
    final var f1 = new FakeService();
    final var f2 = new FakeService();

    s.register(FakeService.class, f0);
    s.register(FakeService.class, f1);
    s.register(FakeService.class, f2);

    assertEquals(
      List.of(f0, f1, f2),
      s.optionalServices(FakeService.class)
    );

    s.close();

    Thread.sleep(500L);
    assertInstanceOf(RPServiceRegistered.class, this.events.poll());
    assertInstanceOf(RPServiceRegistered.class, this.events.poll());
    assertInstanceOf(RPServiceRegistered.class, this.events.poll());
    assertInstanceOf(RPServiceDirectoryClosing.class, this.events.poll());
    assertInstanceOf(RPServiceDirectoryClosed.class, this.events.poll());
    assertEquals(0, this.events.size());
  }

  /**
   * A service that crashes on closing results in an exception.
   *
   * @throws Exception On errors
   */

  @Test
  public void testCrashClosed()
    throws Exception
  {
    final var s = new RPServiceDirectory();
    s.events().subscribe(new PerpetualSubscriber(this.events));

    final var f = new CrashClosedService();
    s.register(CrashClosedService.class, f);

    final var ex = assertThrows(IOException.class, s::close);
    assertTrue(ex.getMessage().contains("Cannot close!"));
  }

  /**
   * Deregistering a service works.
   *
   * @throws Exception On errors
   */

  @Test
  public void testDeregisterGet()
    throws Exception
  {
    final var s = new RPServiceDirectory();
    s.events().subscribe(new PerpetualSubscriber(this.events));

    assertThrows(RPServiceException.class, () -> {
      s.requireService(FakeService.class);
    });

    final var f = new FakeService();
    s.register(FakeService.class, f);
    assertEquals(f, s.requireService(FakeService.class));
    assertEquals(f, s.optionalService(FakeService.class).orElseThrow());

    assertEquals(List.of(f), s.services());
    s.deregister(FakeService.class, f);

    assertThrows(RPServiceException.class, () -> {
      s.requireService(FakeService.class);
    });

    assertEquals(List.of(), s.services());
    s.close();

    Thread.sleep(500L);
    assertInstanceOf(RPServiceRegistered.class, this.events.poll());
    assertInstanceOf(RPServiceDeregistered.class, this.events.poll());
    assertInstanceOf(RPServiceDirectoryClosing.class, this.events.poll());
    assertInstanceOf(RPServiceDirectoryClosed.class, this.events.poll());
    assertEquals(0, this.events.size());
  }

  /**
   * Retrieving registered services works.
   *
   * @throws Exception On errors
   */

  @Test
  public void testDeregisterMultiple()
    throws Exception
  {
    final var s = new RPServiceDirectory();
    s.events().subscribe(new PerpetualSubscriber(this.events));

    assertThrows(RPServiceException.class, () -> {
      s.requireService(FakeService.class);
    });

    final var f0 = new FakeService();
    final var f1 = new FakeService();
    final var f2 = new FakeService();

    s.register(FakeService.class, f0);
    s.register(FakeService.class, f1);
    s.register(FakeService.class, f2);

    assertEquals(
      List.of(f0, f1, f2),
      s.optionalServices(FakeService.class)
    );

    s.deregister(FakeService.class, f1);
    assertEquals(
      List.of(f0, f2),
      s.optionalServices(FakeService.class)
    );

    s.deregister(FakeService.class, f2);
    assertEquals(
      List.of(f0),
      s.optionalServices(FakeService.class)
    );

    s.deregister(FakeService.class, f0);
    assertEquals(
      List.of(),
      s.optionalServices(FakeService.class)
    );

    s.deregister(FakeService.class, f0);
    s.close();

    Thread.sleep(500L);
    assertInstanceOf(RPServiceRegistered.class, this.events.poll());
    assertInstanceOf(RPServiceRegistered.class, this.events.poll());
    assertInstanceOf(RPServiceRegistered.class, this.events.poll());
    assertInstanceOf(RPServiceDeregistered.class, this.events.poll());
    assertInstanceOf(RPServiceDeregistered.class, this.events.poll());
    assertInstanceOf(RPServiceDeregistered.class, this.events.poll());
    assertInstanceOf(RPServiceDirectoryClosing.class, this.events.poll());
    assertInstanceOf(RPServiceDirectoryClosed.class, this.events.poll());
    assertEquals(0, this.events.size());
  }

  /**
   * Deregistering services works.
   *
   * @throws Exception On errors
   */

  @Test
  public void testDeregisterAll()
    throws Exception
  {
    final var s = new RPServiceDirectory();
    s.events().subscribe(new PerpetualSubscriber(this.events));

    assertThrows(RPServiceException.class, () -> {
      s.requireService(FakeService.class);
    });

    final var f0 = new FakeService();
    final var f1 = new FakeService();
    final var f2 = new FakeService();

    s.register(FakeService.class, f0);
    s.register(FakeService.class, f1);
    s.register(FakeService.class, f2);

    assertEquals(
      List.of(f0, f1, f2),
      s.optionalServices(FakeService.class)
    );

    s.deregisterAll(FakeService.class);
    assertEquals(
      List.of(),
      s.optionalServices(FakeService.class)
    );

    s.deregisterAll(FakeService.class);
    s.close();

    Thread.sleep(500L);
    assertInstanceOf(RPServiceRegistered.class, this.events.poll());
    assertInstanceOf(RPServiceRegistered.class, this.events.poll());
    assertInstanceOf(RPServiceRegistered.class, this.events.poll());
    assertInstanceOf(RPServiceDeregistered.class, this.events.poll());
    assertInstanceOf(RPServiceDeregistered.class, this.events.poll());
    assertInstanceOf(RPServiceDeregistered.class, this.events.poll());
    assertInstanceOf(RPServiceDirectoryClosing.class, this.events.poll());
    assertInstanceOf(RPServiceDirectoryClosed.class, this.events.poll());
    assertEquals(0, this.events.size());
  }
}
