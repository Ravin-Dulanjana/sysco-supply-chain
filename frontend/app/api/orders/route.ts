import { NextRequest, NextResponse } from "next/server";

const BACKEND_BASE_URL = process.env.BACKEND_BASE_URL ?? "http://localhost:8080";

async function toClientResponse(response: Response): Promise<NextResponse> {
  const body = await response.text();
  const contentType = response.headers.get("content-type") ?? "application/json";

  return new NextResponse(body, {
    status: response.status,
    headers: { "content-type": contentType },
  });
}

export async function GET(request: NextRequest) {
  try {
    const status = request.nextUrl.searchParams.get("status");
    const url = new URL("/api/orders", BACKEND_BASE_URL);
    if (status) {
      url.searchParams.set("status", status);
    }

    const response = await fetch(url.toString(), { cache: "no-store" });
    return await toClientResponse(response);
  } catch {
    return NextResponse.json(
      { error: "Backend service unavailable." },
      { status: 502 },
    );
  }
}

export async function POST(request: NextRequest) {
  try {
    const payload = await request.text();
    const response = await fetch(`${BACKEND_BASE_URL}/api/orders`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: payload,
    });

    return await toClientResponse(response);
  } catch {
    return NextResponse.json(
      { error: "Backend service unavailable." },
      { status: 502 },
    );
  }
}
