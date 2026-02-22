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

const STATUSES: OrderStatus[] = ["PENDING", "PROCESSING", "SHIPPED", "CANCELLED"];

export default function Home() {
  const [itemName, setItemName] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [filterStatus, setFilterStatus] = useState<"ALL" | OrderStatus>("ALL");

  const [orders, setOrders] = useState<SupplyOrder[]>([]);
  const [draftStatuses, setDraftStatuses] = useState<Record<number, OrderStatus>>({});

  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [updatingOrderId, setUpdatingOrderId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const hasOrders = useMemo(() => orders.length > 0, [orders]);

  const loadOrders = useCallback(async (nextFilter: "ALL" | OrderStatus) => {
    setLoading(true);
    setError(null);
    try {
      const query = nextFilter === "ALL" ? "" : `?status=${nextFilter}`;
      const response = await fetch(`/api/orders${query}`, { cache: "no-store" });
      if (!response.ok) {
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
    void loadOrders("ALL");
  }, [loadOrders]);

  async function handleCreateOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

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
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ itemName: itemName.trim(), quantity: parsedQuantity }),
      });

      if (!response.ok) {
        throw new Error("Could not create order.");
      }

      setItemName("");
      setQuantity("1");
      await loadOrders(filterStatus);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleUpdateStatus(orderId: number) {
    const nextStatus = draftStatuses[orderId];
    if (!nextStatus) return;

    setUpdatingOrderId(orderId);
    setError(null);
    try {
      const response = await fetch(`/api/orders/${orderId}/status`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status: nextStatus }),
      });

      if (!response.ok) {
        throw new Error("Could not update order status.");
      }

      await loadOrders(filterStatus);
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
          <h1 className="text-xl font-semibold text-[#203328]">Supply Order Console</h1>
          <p className="mt-1 text-sm text-[#607064]">
            Basic UI for backend demo: create orders, filter, and update status.
          </p>
        </section>

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
                  void loadOrders(nextFilter);
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
                onClick={() => void loadOrders(filterStatus)}
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
      </div>
    </main>
  );
}
