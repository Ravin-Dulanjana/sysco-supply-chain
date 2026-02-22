"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";

type OrderStatus = "PENDING" | "PROCESSING" | "SHIPPED" | "CANCELLED";

type SupplyOrder = {
  id: number;
  itemName: string;
  quantity: number;
  status: OrderStatus;
  createdAt: string;
  updatedAt: string;
};

type LoginResponse = {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
};

const STATUSES: OrderStatus[] = ["PENDING", "PROCESSING", "SHIPPED", "CANCELLED"];

export default function Home() {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("admin123");
  const [token, setToken] = useState<string | null>(null);

  const [itemName, setItemName] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [filterStatus, setFilterStatus] = useState<"ALL" | OrderStatus>("ALL");

  const [orders, setOrders] = useState<SupplyOrder[]>([]);
  const [draftStatuses, setDraftStatuses] = useState<Record<number, OrderStatus>>({});

  const [loading, setLoading] = useState(false);
  const [loggingIn, setLoggingIn] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [updatingOrderId, setUpdatingOrderId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const hasOrders = useMemo(() => orders.length > 0, [orders]);

  const loadOrders = useCallback(async (nextFilter: "ALL" | OrderStatus, authToken: string) => {
    setLoading(true);
    setError(null);
    try {
      const query = nextFilter === "ALL" ? "" : `?status=${nextFilter}`;
      const response = await fetch(`/api/orders${query}`, {
        cache: "no-store",
        headers: { Authorization: `Bearer ${authToken}` },
      });
      if (!response.ok) {
        if (response.status === 401 || response.status === 403) {
          throw new Error("Session expired. Please log in again.");
        }
        throw new Error("Failed to load orders.");
      }

      const data: SupplyOrder[] = await response.json();
      setOrders(data);
      setDraftStatuses(Object.fromEntries(data.map((order) => [order.id, order.status])));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const savedToken = window.localStorage.getItem("supply-auth-token");
    if (savedToken) {
      setToken(savedToken);
      void loadOrders("ALL", savedToken);
    }
  }, [loadOrders]);

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoggingIn(true);
    setError(null);

    try {
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });
      if (!response.ok) {
        throw new Error("Invalid username or password.");
      }

      const data: LoginResponse = await response.json();
      setToken(data.token);
      window.localStorage.setItem("supply-auth-token", data.token);
      await loadOrders("ALL", data.token);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to log in.");
    } finally {
      setLoggingIn(false);
    }
  }

  function handleLogout() {
    setToken(null);
    setOrders([]);
    setDraftStatuses({});
    setFilterStatus("ALL");
    window.localStorage.removeItem("supply-auth-token");
  }

  async function handleCreateOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) return;

    const parsedQuantity = Number(quantity);
    if (!itemName.trim() || !Number.isInteger(parsedQuantity) || parsedQuantity < 1) {
      setError("Provide a valid item name and a quantity of at least 1.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const response = await fetch("/api/orders", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ itemName: itemName.trim(), quantity: parsedQuantity }),
      });

      if (!response.ok) {
        if (response.status === 401 || response.status === 403) {
          throw new Error("Session expired. Please log in again.");
        }
        throw new Error("Could not create order.");
      }

      setItemName("");
      setQuantity("1");
      await loadOrders(filterStatus, token);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleUpdateStatus(orderId: number) {
    if (!token) return;
    const nextStatus = draftStatuses[orderId];
    if (!nextStatus) return;

    setUpdatingOrderId(orderId);
    setError(null);
    try {
      const response = await fetch(`/api/orders/${orderId}/status`, {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ status: nextStatus }),
      });

      if (!response.ok) {
        if (response.status === 401 || response.status === 403) {
          throw new Error("Session expired. Please log in again.");
        }
        throw new Error("Could not update order status.");
      }

      await loadOrders(filterStatus, token);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong.");
    } finally {
      setUpdatingOrderId(null);
    }
  }

  return (
    <main className="min-h-screen bg-[#f6f7f5] px-5 py-8 md:px-8">
      <div className="mx-auto flex w-full max-w-5xl flex-col gap-6">
        <section className="rounded-xl border border-[#d9ddd8] bg-white p-5">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h1 className="text-xl font-semibold text-[#203328]">Supply Order Console</h1>
              <p className="mt-1 text-sm text-[#607064]">
                Minimal UI for microservices demo: login via auth-service and manage orders via order-service.
              </p>
            </div>
            {token ? (
              <button
                type="button"
                onClick={handleLogout}
                className="h-9 rounded-md border border-[#cfd7d0] px-3 text-sm text-[#314739]"
              >
                Logout
              </button>
            ) : null}
          </div>
        </section>

        {!token ? (
          <section className="rounded-xl border border-[#d9ddd8] bg-white p-5">
            <h2 className="text-base font-semibold text-[#24362b]">Login</h2>
            <p className="mt-1 text-sm text-[#607064]">Demo user: admin / admin123</p>
            <form className="mt-4 grid gap-3 md:grid-cols-[1fr_1fr_auto]" onSubmit={handleLogin}>
              <input
                type="text"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="Username"
                className="h-10 rounded-md border border-[#cfd7d0] px-3 text-sm outline-none focus:border-[#6d8a74]"
              />
              <input
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Password"
                className="h-10 rounded-md border border-[#cfd7d0] px-3 text-sm outline-none focus:border-[#6d8a74]"
              />
              <button
                type="submit"
                disabled={loggingIn}
                className="h-10 rounded-md bg-[#2f4a3a] px-4 text-sm font-medium text-white disabled:opacity-60"
              >
                {loggingIn ? "Signing in..." : "Sign in"}
              </button>
            </form>
            {error && <p className="mt-3 text-sm text-[#b33f3f]">{error}</p>}
          </section>
        ) : null}

        {token ? (
          <>
            <section className="rounded-xl border border-[#d9ddd8] bg-white p-5">
              <h2 className="text-base font-semibold text-[#24362b]">Create Order</h2>
              <form className="mt-4 grid gap-3 md:grid-cols-[1fr_150px_auto]" onSubmit={handleCreateOrder}>
                <input
                  type="text"
                  value={itemName}
                  onChange={(event) => setItemName(event.target.value)}
                  placeholder="Item name"
                  className="h-10 rounded-md border border-[#cfd7d0] px-3 text-sm outline-none focus:border-[#6d8a74]"
                />
                <input
                  type="number"
                  min={1}
                  value={quantity}
                  onChange={(event) => setQuantity(event.target.value)}
                  className="h-10 rounded-md border border-[#cfd7d0] px-3 text-sm outline-none focus:border-[#6d8a74]"
                />
                <button
                  type="submit"
                  disabled={submitting}
                  className="h-10 rounded-md bg-[#2f4a3a] px-4 text-sm font-medium text-white disabled:opacity-60"
                >
                  {submitting ? "Creating..." : "Create"}
                </button>
              </form>
            </section>

            <section className="rounded-xl border border-[#d9ddd8] bg-white p-5">
              <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                <h2 className="text-base font-semibold text-[#24362b]">Orders</h2>
                <div className="flex items-center gap-2">
                  <select
                    value={filterStatus}
                    onChange={(event) => {
                      const nextFilter = event.target.value as "ALL" | OrderStatus;
                      setFilterStatus(nextFilter);
                      void loadOrders(nextFilter, token);
                    }}
                    className="h-9 rounded-md border border-[#cfd7d0] bg-white px-3 text-sm outline-none focus:border-[#6d8a74]"
                  >
                    <option value="ALL">All statuses</option>
                    {STATUSES.map((status) => (
                      <option key={status} value={status}>
                        {status}
                      </option>
                    ))}
                  </select>
                  <button
                    type="button"
                    onClick={() => void loadOrders(filterStatus, token)}
                    className="h-9 rounded-md border border-[#cfd7d0] bg-white px-3 text-sm text-[#314739]"
                  >
                    Refresh
                  </button>
                </div>
              </div>

              {error && <p className="mt-3 text-sm text-[#b33f3f]">{error}</p>}

              <div className="mt-4 overflow-x-auto">
                <table className="w-full min-w-[680px] border-collapse">
                  <thead>
                    <tr className="border-b border-[#e4e7e3] text-left text-xs uppercase tracking-wide text-[#75857a]">
                      <th className="py-2 pr-3">ID</th>
                      <th className="py-2 pr-3">Item</th>
                      <th className="py-2 pr-3">Qty</th>
                      <th className="py-2 pr-3">Status</th>
                      <th className="py-2 pr-3">Created</th>
                      <th className="py-2 pr-3">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {loading ? (
                      <tr>
                        <td className="py-4 text-sm text-[#708175]" colSpan={6}>
                          Loading orders...
                        </td>
                      </tr>
                    ) : null}

                    {!loading && !hasOrders ? (
                      <tr>
                        <td className="py-4 text-sm text-[#708175]" colSpan={6}>
                          No orders found.
                        </td>
                      </tr>
                    ) : null}

                    {!loading &&
                      orders.map((order) => (
                        <tr key={order.id} className="border-b border-[#eef1ed] text-sm text-[#2b3b31]">
                          <td className="py-3 pr-3">#{order.id}</td>
                          <td className="py-3 pr-3">{order.itemName}</td>
                          <td className="py-3 pr-3">{order.quantity}</td>
                          <td className="py-3 pr-3">{order.status}</td>
                          <td className="py-3 pr-3">{new Date(order.createdAt).toLocaleString()}</td>
                          <td className="py-3 pr-3">
                            <div className="flex items-center gap-2">
                              <select
                                value={draftStatuses[order.id] ?? order.status}
                                onChange={(event) =>
                                  setDraftStatuses((current) => ({
                                    ...current,
                                    [order.id]: event.target.value as OrderStatus,
                                  }))
                                }
                                className="h-8 rounded-md border border-[#cfd7d0] bg-white px-2 text-xs outline-none focus:border-[#6d8a74]"
                              >
                                {STATUSES.map((status) => (
                                  <option key={status} value={status}>
                                    {status}
                                  </option>
                                ))}
                              </select>
                              <button
                                type="button"
                                onClick={() => void handleUpdateStatus(order.id)}
                                disabled={updatingOrderId === order.id}
                                className="h-8 rounded-md bg-[#385a46] px-3 text-xs font-medium text-white disabled:opacity-60"
                              >
                                {updatingOrderId === order.id ? "Saving..." : "Save"}
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </div>
            </section>
          </>
        ) : null}
      </div>
    </main>
  );
}
